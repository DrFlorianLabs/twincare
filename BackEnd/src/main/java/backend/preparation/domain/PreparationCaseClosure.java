package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;

public record PreparationCaseClosure(
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        long closedByUserId,
        Instant closedAt,
        long readyCaseRevision,
        long closedCaseRevision) {

    public PreparationCaseClosure {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(closedAt, "Closure timestamp is required");
        if (closedByUserId <= 0
                || readyCaseRevision < 1
                || closedCaseRevision != readyCaseRevision + 1) {
            throw new IllegalArgumentException("Preparation case closure evidence is invalid");
        }
    }
}
