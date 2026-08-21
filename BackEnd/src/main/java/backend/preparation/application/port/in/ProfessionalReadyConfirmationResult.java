package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;

public record ProfessionalReadyConfirmationResult(
        long confirmedByUserId,
        Instant confirmedAt,
        String ruleSetId,
        int ruleSetVersion,
        long reviewedCaseRevision) {

    public ProfessionalReadyConfirmationResult {
        if (confirmedByUserId <= 0 || ruleSetVersion <= 0 || reviewedCaseRevision < 0) {
            throw new IllegalArgumentException("Readiness confirmation evidence is invalid");
        }
        Objects.requireNonNull(confirmedAt, "Readiness confirmation timestamp is required");
        Objects.requireNonNull(ruleSetId, "Readiness rule set is required");
    }
}
