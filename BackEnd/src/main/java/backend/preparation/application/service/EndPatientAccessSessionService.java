package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.EndPatientAccessSessionCommand;
import backend.preparation.application.port.in.EndPatientAccessSessionUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;

public final class EndPatientAccessSessionService implements EndPatientAccessSessionUseCase {

    private final PreparationCaseRepository preparationCases;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository sessions;
    private final Clock clock;

    public EndPatientAccessSessionService(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository sessions,
            Clock clock) {
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.invitations = Objects.requireNonNull(invitations);
        this.sessions = Objects.requireNonNull(sessions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void end(EndPatientAccessSessionCommand command) {
        if (command == null || command.grant() == null) {
            throw new PatientSessionUnavailableException();
        }
        var grant = command.grant();
        Instant now = clock.instant();

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

        session.revoke(now);
        sessions.update(session);
    }
}
