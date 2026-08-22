package backend.preparation.adapter.in.web;

import java.util.List;

public record PreparationAuditEventPageResponse(
        List<PreparationAuditEventResponse> items,
        Long nextBeforeSequence,
        boolean completeFromCaseCreation) {

    public PreparationAuditEventPageResponse {
        items = List.copyOf(items);
    }

    @Override
    public String toString() {
        return "PreparationAuditEventPageResponse[metadata redacted]";
    }
}
