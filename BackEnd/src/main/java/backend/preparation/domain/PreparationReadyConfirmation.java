package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;

public record PreparationReadyConfirmation(
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        long confirmedByUserId,
        Instant confirmedAt,
        String ruleSetId,
        int ruleSetVersion,
        long reviewedCaseRevision,
        Instant reviewedReasonRecordedAt,
        int openTaskCount) {

    public PreparationReadyConfirmation {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        if (confirmedByUserId <= 0) {
            throw new IllegalArgumentException("Readiness confirmer is invalid");
        }
        Objects.requireNonNull(confirmedAt, "Readiness confirmation timestamp is required");
        if (ruleSetId == null || ruleSetId.isBlank() || ruleSetId.length() > 64 || ruleSetVersion <= 0) {
            throw new IllegalArgumentException("Readiness rule reference is invalid");
        }
        if (reviewedCaseRevision < 0 || openTaskCount != 0) {
            throw new IllegalArgumentException("Readiness review evidence is invalid");
        }
        Objects.requireNonNull(reviewedReasonRecordedAt, "Reviewed reason timestamp is required");
    }
}
