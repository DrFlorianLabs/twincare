package backend.clinicalcontext.adapter.in.web;

import backend.clinicalcontext.domain.FactReviewDecision;
import jakarta.validation.constraints.NotNull;

public record ClinicalFactReviewRequest(@NotNull FactReviewDecision decision) {
}
