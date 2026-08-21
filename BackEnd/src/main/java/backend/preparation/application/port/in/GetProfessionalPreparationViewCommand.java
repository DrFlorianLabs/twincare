package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public record GetProfessionalPreparationViewCommand(
        OrganizationId organizationId,
        PreparationCaseId caseId) {

    public GetProfessionalPreparationViewCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
    }
}
