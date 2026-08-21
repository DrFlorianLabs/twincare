package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;

import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadinessAssessment;

public record PreparationReadyConfirmationResult(
        PreparationCaseStatus caseStatus,
        PreparationReadinessAssessment readiness,
        long confirmedByUserId,
        Instant confirmedAt,
        String ruleSetId,
        int ruleSetVersion,
        long reviewedCaseRevision) {

    public PreparationReadyConfirmationResult {
        Objects.requireNonNull(caseStatus, "Preparation case status is required");
        Objects.requireNonNull(readiness, "Preparation readiness is required");
        if (confirmedByUserId <= 0) {
            throw new IllegalArgumentException("Readiness confirmer is invalid");
        }
        Objects.requireNonNull(confirmedAt, "Readiness confirmation timestamp is required");
        Objects.requireNonNull(ruleSetId, "Readiness rule set is required");
        if (ruleSetVersion <= 0) {
            throw new IllegalArgumentException("Readiness rule version is invalid");
        }
        if (reviewedCaseRevision < 0) {
            throw new IllegalArgumentException("Reviewed case revision is invalid");
        }
    }
}
