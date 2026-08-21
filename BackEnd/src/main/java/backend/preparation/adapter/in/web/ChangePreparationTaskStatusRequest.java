package backend.preparation.adapter.in.web;

import backend.preparation.domain.PreparationTaskStatus;
import jakarta.validation.constraints.NotNull;

public record ChangePreparationTaskStatusRequest(
        @NotNull PreparationTaskStatus status) {
}
