package backend.clinicalcontext.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record ClinicalFactReviewResponse(
        UUID factId,
        String status,
        Instant reviewedAt) {
}
