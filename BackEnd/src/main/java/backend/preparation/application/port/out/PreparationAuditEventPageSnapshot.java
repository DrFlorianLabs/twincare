package backend.preparation.application.port.out;

import java.util.List;

public record PreparationAuditEventPageSnapshot(
        List<StoredPreparationAuditEvent> events,
        boolean hasMore,
        boolean completeFromCaseCreation) {

    public PreparationAuditEventPageSnapshot {
        events = List.copyOf(events);
    }
}
