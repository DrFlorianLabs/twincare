package backend.clinicalcontext.application.port.in;

import java.util.Objects;

import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.FactReviewDecision;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public record ReviewClinicalFactCommand(
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        ClinicalFactId factId,
        FactReviewDecision decision) {

    public ReviewClinicalFactCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(factId, "Clinical fact identifier is required");
        Objects.requireNonNull(decision, "Fact review decision is required");
    }
}
