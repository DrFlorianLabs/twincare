package backend.preparation.adapter.in.web;

import java.time.Instant;

public record PreparationReadyConfirmationResponse(
        String caseStatus,
        PreparationReadinessResponse readiness,
        long confirmedByUserId,
        Instant confirmedAt,
        String ruleSetId,
        int ruleSetVersion,
        long reviewedCaseRevision) {
}
