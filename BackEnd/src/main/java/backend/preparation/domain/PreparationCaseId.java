package backend.preparation.domain;

import java.util.Objects;
import java.util.UUID;

public record PreparationCaseId(UUID value) {

    public PreparationCaseId {
        Objects.requireNonNull(value, "Preparation case identifier is required");
    }

    public static PreparationCaseId newId() {
        return new PreparationCaseId(UUID.randomUUID());
    }
}
