package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;

public record RevokePatientInvitationCommand(
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        PatientInvitationId invitationId) {

    public RevokePatientInvitationCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(invitationId, "Patient invitation identifier is required");
    }
}
