package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public record IssuePatientInvitationCommand(
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId) {

    public IssuePatientInvitationCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
    }
}
