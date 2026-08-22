package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;

import backend.preparation.domain.PreparationCaseStatus;

public record ClosePreparationCaseResult(
        PreparationCaseStatus caseStatus,
        long revision,
        long closedByUserId,
        Instant closedAt,
        long readyCaseRevision) {

    public ClosePreparationCaseResult {
        Objects.requireNonNull(caseStatus, "Preparation case status is required");
        Objects.requireNonNull(closedAt, "Closure timestamp is required");
        if (caseStatus != PreparationCaseStatus.CLOSED
                || revision < 1
                || closedByUserId <= 0
                || readyCaseRevision < 1
                || revision != readyCaseRevision + 1) {
            throw new IllegalArgumentException("Preparation case closure result is invalid");
        }
    }
}
