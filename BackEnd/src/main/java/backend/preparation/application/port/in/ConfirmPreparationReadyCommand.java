package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public record ConfirmPreparationReadyCommand(
        OrganizationId organizationId,
        PreparationCaseId caseId,
        long expectedRevision) {

    public ConfirmPreparationReadyCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Expected preparation case revision is invalid");
        }
    }
}
