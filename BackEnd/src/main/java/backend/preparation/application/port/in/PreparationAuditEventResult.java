package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import backend.preparation.domain.PreparationAuditActorKind;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubject;

public record PreparationAuditEventResult(
        long sequence,
        PreparationAuditEventType type,
        int eventVersion,
        PreparationAuditActorKind actorKind,
        OptionalLong actorUserId,
        Instant occurredAt,
        long caseRevision,
        Optional<PreparationAuditSubject> subject) {
}
