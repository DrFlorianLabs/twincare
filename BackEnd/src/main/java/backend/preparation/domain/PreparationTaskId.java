package backend.preparation.domain;

import java.util.Objects;
import java.util.UUID;

public record PreparationTaskId(UUID value) {

    public PreparationTaskId {
        Objects.requireNonNull(value, "Preparation task identifier is required");
    }
}
