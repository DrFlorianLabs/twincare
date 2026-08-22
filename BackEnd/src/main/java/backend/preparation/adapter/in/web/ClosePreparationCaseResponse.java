package backend.preparation.adapter.in.web;

import java.time.Instant;

public record ClosePreparationCaseResponse(
        String caseStatus,
        long revision,
        long closedByUserId,
        Instant closedAt,
        long readyCaseRevision) {
}
