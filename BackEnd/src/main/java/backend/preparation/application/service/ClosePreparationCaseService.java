package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.PreparationWorkflowConflictException;
import backend.preparation.application.port.in.ClosePreparationCaseCommand;
import backend.preparation.application.port.in.ClosePreparationCaseResult;
import backend.preparation.application.port.in.ClosePreparationCaseUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseClosureRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationReadyConfirmationRepository;
import backend.preparation.application.port.out.PreparationTaskRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubject;
import backend.preparation.domain.PreparationAuditSubjectType;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseClosure;
import backend.preparation.domain.PreparationCaseStatus;

public final class ClosePreparationCaseService implements ClosePreparationCaseUseCase {

    private final ProfessionalPreparationAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PreparationReadyConfirmationRepository confirmations;
    private final PreparationTaskRepository tasks;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository accessSessions;
    private final PreparationCaseClosureRepository closures;
    private final PreparationAuditEventStore auditEvents;
    private final Clock clock;

    public ClosePreparationCaseService(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationReadyConfirmationRepository confirmations,
            PreparationTaskRepository tasks,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PreparationCaseClosureRepository closures,
            PreparationAuditEventStore auditEvents,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.tasks = Objects.requireNonNull(tasks);
        this.invitations = Objects.requireNonNull(invitations);
        this.accessSessions = Objects.requireNonNull(accessSessions);
        this.closures = Objects.requireNonNull(closures);
        this.auditEvents = Objects.requireNonNull(auditEvents);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ClosePreparationCaseResult close(ClosePreparationCaseCommand command) {
        Objects.requireNonNull(command, "Preparation case closure command is required");
        long actorUserId = authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.CLOSE_PREPARATION_CASE);
        PreparationCase preparationCase = preparationCases.findByIdForUpdate(
                        command.caseId(), command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);

        var existingClosure = closures.findByCase(preparationCase.id(), preparationCase.organizationId());
        if (existingClosure.isPresent()) {
            return replay(command, preparationCase, existingClosure.orElseThrow());
        }

        var confirmation = confirmations.findByCase(
                        preparationCase.id(), preparationCase.organizationId())
                .orElseThrow(PreparationWorkflowConflictException::new);
        long openTaskCount = tasks.countOpen(preparationCase.id(), preparationCase.organizationId());
        if (preparationCase.status() != PreparationCaseStatus.READY_FOR_CONSULTATION
                || preparationCase.revision() != command.expectedRevision()
                || preparationCase.revision() < 1
                || openTaskCount != 0
                || confirmation.openTaskCount() != 0
                || confirmation.reviewedCaseRevision() != preparationCase.revision() - 1
                || preparationCase.patientReason()
                        .filter(reason -> reason.recordedAt().equals(
                                confirmation.reviewedReasonRecordedAt()))
                        .isEmpty()
                || confirmation.confirmedAt().isBefore(confirmation.reviewedReasonRecordedAt())
                || !preparationCase.updatedAt().equals(confirmation.confirmedAt())) {
            throw new PreparationWorkflowConflictException();
        }

        var caseInvitations = invitations.findAllByCaseForUpdate(
                preparationCase.id(), preparationCase.organizationId());
        var caseSessions = accessSessions.findAllByCaseForUpdate(
                preparationCase.id(), preparationCase.organizationId());
        Instant now = now();
        preparationCase.close(now);
        long closedRevision = preparationCases.update(preparationCase);
        var closure = new PreparationCaseClosure(
                preparationCase.organizationId(),
                preparationCase.id(),
                actorUserId,
                now,
                command.expectedRevision(),
                closedRevision);
        closures.add(closure);

        caseInvitations.stream()
                .filter(invitation -> invitation.status() == PatientInvitationStatus.ACTIVE
                        || invitation.status() == PatientInvitationStatus.CONSUMED)
                .forEach(invitation -> {
                    invitation.revoke(now);
                    invitations.update(invitation);
                    auditEvents.append(PreparationAuditEvent.staff(
                            preparationCase.organizationId(),
                            preparationCase.id(),
                            PreparationAuditEventType.INVITATION_REVOKED,
                            actorUserId,
                            now,
                            closedRevision,
                            Optional.of(new PreparationAuditSubject(
                                    PreparationAuditSubjectType.INVITATION,
                                    invitation.id().value()))));
                });
        caseSessions.stream()
                .filter(session -> session.revokedAt().isEmpty())
                .forEach(session -> {
                    session.revoke(now);
                    accessSessions.update(session);
                    auditEvents.append(PreparationAuditEvent.staff(
                            preparationCase.organizationId(),
                            preparationCase.id(),
                            PreparationAuditEventType.PATIENT_ACCESS_REVOKED,
                            actorUserId,
                            now,
                            closedRevision,
                            Optional.empty()));
                });
        auditEvents.append(PreparationAuditEvent.staff(
                preparationCase.organizationId(),
                preparationCase.id(),
                PreparationAuditEventType.PREPARATION_CLOSED,
                actorUserId,
                now,
                closedRevision,
                Optional.empty()));
        return result(closure);
    }

    private ClosePreparationCaseResult replay(
            ClosePreparationCaseCommand command,
            PreparationCase preparationCase,
            PreparationCaseClosure closure) {
        if (preparationCase.status() != PreparationCaseStatus.CLOSED
                || command.expectedRevision() != closure.readyCaseRevision()
                || preparationCase.revision() != closure.closedCaseRevision()) {
            throw new PreparationWorkflowConflictException();
        }
        return result(closure);
    }

    private ClosePreparationCaseResult result(PreparationCaseClosure closure) {
        return new ClosePreparationCaseResult(
                PreparationCaseStatus.CLOSED,
                closure.closedCaseRevision(),
                closure.closedByUserId(),
                closure.closedAt(),
                closure.readyCaseRevision());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
