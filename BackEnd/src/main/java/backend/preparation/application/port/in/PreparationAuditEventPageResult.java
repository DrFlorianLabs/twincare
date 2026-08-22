package backend.preparation.application.port.in;

import java.util.List;
import java.util.OptionalLong;

public record PreparationAuditEventPageResult(
        List<PreparationAuditEventResult> items,
        OptionalLong nextBeforeSequence,
        boolean completeFromCaseCreation) {

    public PreparationAuditEventPageResult {
        items = List.copyOf(items);
    }
}
