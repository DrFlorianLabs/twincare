package backend.preparation.domain;

import java.util.List;
import java.util.Objects;

public record PreparationReadinessAssessment(
        String ruleSetId,
        int ruleSetVersion,
        PreparationReadinessState state,
        List<PreparationReadinessBlocker> blockers,
        PreparationReadinessProvenance provenance) {

    public PreparationReadinessAssessment {
        if (ruleSetId == null || ruleSetId.isBlank()) {
            throw new IllegalArgumentException("Readiness rule-set identifier is required");
        }
        if (ruleSetVersion <= 0) {
            throw new IllegalArgumentException("Readiness rule-set version must be positive");
        }
        Objects.requireNonNull(state, "Readiness state is required");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "Readiness blockers are required"));
        Objects.requireNonNull(provenance, "Readiness provenance is required");
    }
}
