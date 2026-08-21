package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.PreparationTaskDescription;
import backend.preparation.domain.PreparationTaskId;
import backend.preparation.domain.PreparationTaskStatus;

public record PreparationTaskResult(
        PreparationTaskId id,
        PreparationTaskDescription description,
        long assigneeUserId,
        PreparationTaskStatus status,
        long createdByUserId,
        Instant createdAt,
        Optional<Long> closedByUserId,
        Optional<Instant> closedAt,
        Instant updatedAt) {

    public PreparationTaskResult {
        Objects.requireNonNull(id, "Preparation task identifier is required");
        Objects.requireNonNull(description, "Preparation task description is required");
        if (assigneeUserId <= 0) {
            throw new IllegalArgumentException("Preparation task assignee is invalid");
        }
        Objects.requireNonNull(status, "Preparation task status is required");
        if (createdByUserId <= 0) {
            throw new IllegalArgumentException("Preparation task creator is invalid");
        }
        Objects.requireNonNull(createdAt, "Preparation task creation timestamp is required");
        Objects.requireNonNull(closedByUserId, "Closer container is required");
        Objects.requireNonNull(closedAt, "Closure timestamp container is required");
        Objects.requireNonNull(updatedAt, "Preparation task update timestamp is required");
    }

    @Override
    public String toString() {
        return "PreparationTaskResult[id=" + id + ", description=redacted, status=" + status + "]";
    }
}
