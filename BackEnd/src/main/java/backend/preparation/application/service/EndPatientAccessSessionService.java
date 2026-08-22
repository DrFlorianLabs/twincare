package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.EndPatientAccessSessionCommand;
import backend.preparation.application.port.in.EndPatientAccessSessionUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;

public final class EndPatientAccessSessionService implements EndPatientAccessSessionUseCase {

    private final PreparationCaseRepository preparationCases;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository sessions;
    private final PreparationAuditEventStore auditEvents;
    private final Clock clock;

    public EndPatientAccessSessionService(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository sessions,
            PreparationAuditEventStore auditEvents,
            Clock clock) {
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.invitations = Objects.requireNonNull(invitations);
        this.sessions = Objects.requireNonNull(sessions);
        this.auditEvents = Objects.requireNonNull(auditEvents);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void end(EndPatientAccessSessionCommand command) {
        if (command == null || command.grant() == null) {
            throw new PatientSessionUnavailableException();
        }
        var grant = command.grant();

        var preparationCase = preparationCases
                .findByIdForUpdate(grant.preparationCaseId(), grant.organizationId())
                .orElse(null);
        if (preparationCase == null) {
            return;
        }
        var invitation = invitations
                .findByIdForUpdate(
                        grant.invitationId(), preparationCase.id(), preparationCase.organizationId())
                .orElse(null);
        if (invitation == null) {
            return;
        }
        var session = sessions
                .findByIdForUpdate(
                        grant.sessionId(),
                        invitation.id(),
                        preparationCase.id(),
                        preparationCase.organizationId())
                .orElse(null);
        if (session == null || session.revokedAt().isPresent()) {
            return;
        }

        Instant now = clock.instant();
        session.revoke(now);
        sessions.update(session);
        auditEvents.append(PreparationAuditEvent.patient(
                preparationCase.organizationId(),
                preparationCase.id(),
                PreparationAuditEventType.PATIENT_ACCESS_ENDED,
                now,
                preparationCase.revision(),
                Optional.empty()));
    }
}
