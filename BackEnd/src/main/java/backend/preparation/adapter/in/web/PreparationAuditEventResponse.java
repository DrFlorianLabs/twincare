package backend.preparation.adapter.in.web;

import java.time.Instant;

public record PreparationAuditEventResponse(
        long sequence,
        String type,
        int eventVersion,
        String actorKind,
        Long actorUserId,
        Instant occurredAt,
        long caseRevision,
        PreparationAuditSubjectResponse subject) {

    @Override
    public String toString() {
        return "PreparationAuditEventResponse[metadata redacted]";
    }
}
