package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.RevokePatientInvitationCommand;
import backend.preparation.application.port.in.RevokePatientInvitationUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubject;
import backend.preparation.domain.PreparationAuditSubjectType;
import backend.preparation.domain.PreparationCase;

public final class RevokePatientInvitationService implements RevokePatientInvitationUseCase {

    private final ProfessionalPreparationAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository accessSessions;
    private final PreparationAuditEventStore auditEvents;
    private final Clock clock;

    public RevokePatientInvitationService(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PreparationAuditEventStore auditEvents,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.invitations = Objects.requireNonNull(invitations);
        this.accessSessions = Objects.requireNonNull(accessSessions);
        this.auditEvents = Objects.requireNonNull(auditEvents);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void revoke(RevokePatientInvitationCommand command) {
        Objects.requireNonNull(command, "Patient invitation revocation command is required");
        long actorUserId = authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.ISSUE_PATIENT_INVITATION);
        var preparationCase = preparationCases.findByIdForUpdate(
                        command.preparationCaseId(), command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);
        var invitation = invitations.findByIdForUpdate(
                        command.invitationId(),
                        command.preparationCaseId(),
                        command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);
        Instant now = clock.instant();
        if (invitation.status() == PatientInvitationStatus.REVOKED) {
            if (accessSessions.revokeByInvitationId(invitation.id(), now)) {
                appendAccessRevoked(preparationCase, actorUserId, now);
            }
            return;
        }
        invitation.revoke(now);
        invitations.update(invitation);
        boolean accessRevoked = accessSessions.revokeByInvitationId(invitation.id(), now);
        auditEvents.append(PreparationAuditEvent.staff(
                preparationCase.organizationId(),
                preparationCase.id(),
                PreparationAuditEventType.INVITATION_REVOKED,
                actorUserId,
                now,
                preparationCase.revision(),
                Optional.of(new PreparationAuditSubject(
                        PreparationAuditSubjectType.INVITATION,
                        invitation.id().value()))));
        if (accessRevoked) {
            appendAccessRevoked(preparationCase, actorUserId, now);
        }
    }

    private void appendAccessRevoked(
            PreparationCase preparationCase,
            long actorUserId,
            Instant occurredAt) {
        auditEvents.append(PreparationAuditEvent.staff(
                preparationCase.organizationId(),
                preparationCase.id(),
                PreparationAuditEventType.PATIENT_ACCESS_REVOKED,
                actorUserId,
                occurredAt,
                preparationCase.revision(),
                Optional.empty()));
    }
}
