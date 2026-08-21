package backend.preparation.adapter.in.web;

import java.time.Instant;

public record ProfessionalReadyConfirmationResponse(
        boolean confirmed,
        Long confirmedByUserId,
        Instant confirmedAt,
        String ruleSetId,
        Integer ruleSetVersion,
        Long reviewedCaseRevision) {
}
