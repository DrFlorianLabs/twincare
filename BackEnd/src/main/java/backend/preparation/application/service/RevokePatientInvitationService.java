package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.RevokePatientInvitationCommand;
import backend.preparation.application.port.in.RevokePatientInvitationUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationCaseRepository;

public final class RevokePatientInvitationService implements RevokePatientInvitationUseCase {

    private final PreparationCaseAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository accessSessions;
    private final Clock clock;

    public RevokePatientInvitationService(
            PreparationCaseAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.invitations = Objects.requireNonNull(invitations);
        this.accessSessions = Objects.requireNonNull(accessSessions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void revoke(RevokePatientInvitationCommand command) {
        Objects.requireNonNull(command, "Patient invitation revocation command is required");
        authorization.requireStaffAccess(command.organizationId());
        preparationCases.findByIdForUpdate(command.preparationCaseId(), command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);
        var invitation = invitations.findByIdForUpdate(
                        command.invitationId(),
                        command.preparationCaseId(),
                        command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);
        Instant now = clock.instant();
        invitation.revoke(now);
        invitations.update(invitation);
        accessSessions.revokeByInvitationId(invitation.id(), now);
    }
}
