package backend.clinicalcontext.application.port.in;

import java.time.Instant;

import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.FactReviewStatus;

public record ReviewClinicalFactResult(
        ClinicalFactId factId,
        FactReviewStatus status,
        Instant reviewedAt) {
}
