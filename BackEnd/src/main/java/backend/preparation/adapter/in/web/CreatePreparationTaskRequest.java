package backend.preparation.adapter.in.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePreparationTaskRequest(
        @NotNull UUID requestId,
        @NotBlank String label,
        @Positive Long assigneeUserId) {

    @Override
    public String toString() {
        return "CreatePreparationTaskRequest[requestId=" + requestId
                + ", label redacted, assigneeUserId=" + assigneeUserId + "]";
    }
}
