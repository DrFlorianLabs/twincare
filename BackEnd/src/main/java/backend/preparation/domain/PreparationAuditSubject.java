package backend.preparation.domain;

import java.util.Objects;
import java.util.UUID;

public record PreparationAuditSubject(
        PreparationAuditSubjectType type,
        UUID id) {

    public PreparationAuditSubject {
        Objects.requireNonNull(type, "Audit subject type is required");
        Objects.requireNonNull(id, "Audit subject identifier is required");
    }
}
