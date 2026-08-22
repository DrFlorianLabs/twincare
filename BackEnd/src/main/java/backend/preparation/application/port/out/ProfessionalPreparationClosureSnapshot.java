package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;

public record ProfessionalPreparationClosureSnapshot(
        long closedByUserId,
        Instant closedAt,
        long readyCaseRevision,
        long closedCaseRevision) {

    public ProfessionalPreparationClosureSnapshot {
        Objects.requireNonNull(closedAt, "Closure timestamp is required");
        if (closedByUserId <= 0
                || readyCaseRevision < 1
                || closedCaseRevision != readyCaseRevision + 1) {
            throw new IllegalArgumentException("Preparation closure evidence is invalid");
        }
    }
}
