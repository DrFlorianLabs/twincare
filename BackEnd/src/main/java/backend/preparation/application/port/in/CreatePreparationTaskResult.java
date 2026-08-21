package backend.preparation.application.port.in;

import java.util.Objects;

public record CreatePreparationTaskResult(
        PreparationTaskResult task,
        boolean created) {

    public CreatePreparationTaskResult {
        Objects.requireNonNull(task, "Preparation task result is required");
    }
}
