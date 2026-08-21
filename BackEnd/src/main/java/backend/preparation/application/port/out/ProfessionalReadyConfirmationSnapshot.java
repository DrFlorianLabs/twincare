package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;

public record ProfessionalReadyConfirmationSnapshot(
        long confirmedByUserId,
        Instant confirmedAt,
        String ruleSetId,
        int ruleSetVersion,
        long reviewedCaseRevision,
        Instant reviewedReasonRecordedAt,
        int openTaskCount) {

    public ProfessionalReadyConfirmationSnapshot {
        if (confirmedByUserId <= 0
                || ruleSetVersion <= 0
                || reviewedCaseRevision < 0
                || openTaskCount != 0) {
            throw new IllegalArgumentException("Readiness confirmation evidence is invalid");
        }
        Objects.requireNonNull(confirmedAt, "Readiness confirmation timestamp is required");
        Objects.requireNonNull(ruleSetId, "Readiness rule set is required");
        Objects.requireNonNull(reviewedReasonRecordedAt, "Reviewed reason timestamp is required");
    }
}
