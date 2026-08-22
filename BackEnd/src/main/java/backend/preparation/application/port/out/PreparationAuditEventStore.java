package backend.preparation.application.port.out;

import backend.preparation.domain.PreparationAuditEvent;

public interface PreparationAuditEventStore {

    void append(PreparationAuditEvent event);
}
