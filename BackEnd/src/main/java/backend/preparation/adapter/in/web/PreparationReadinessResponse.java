package backend.preparation.adapter.in.web;

import java.util.List;

public record PreparationReadinessResponse(
        String ruleSetId,
        int ruleSetVersion,
        String state,
        List<String> blockers,
        String provenance) {
}
