package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public record ClosePreparationCaseCommand(
        OrganizationId organizationId,
        PreparationCaseId caseId,
        long expectedRevision) {

    public ClosePreparationCaseCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        if (expectedRevision < 1) {
            throw new IllegalArgumentException("Expected preparation case revision is invalid");
        }
    }
}
