package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.PreparationTaskDescription;
import backend.preparation.domain.PreparationTaskId;
import backend.preparation.domain.PreparationTaskStatus;

public record ProfessionalPreparationTaskSnapshot(
        PreparationTaskId id,
        PreparationTaskDescription description,
        long assigneeUserId,
        PreparationTaskStatus status,
        long createdByUserId,
        Instant createdAt,
        Optional<Long> closedByUserId,
        Optional<Instant> closedAt,
        Instant updatedAt) {

    public ProfessionalPreparationTaskSnapshot {
        Objects.requireNonNull(id, "Preparation task identifier is required");
        Objects.requireNonNull(description, "Preparation task description is required");
        if (assigneeUserId <= 0 || createdByUserId <= 0) {
            throw new IllegalArgumentException("Preparation task actor is invalid");
        }
        Objects.requireNonNull(status, "Preparation task status is required");
        Objects.requireNonNull(createdAt, "Preparation task creation timestamp is required");
        Objects.requireNonNull(closedByUserId, "Closer container is required");
        Objects.requireNonNull(closedAt, "Closure timestamp container is required");
        Objects.requireNonNull(updatedAt, "Preparation task update timestamp is required");
    }

    @Override
    public String toString() {
        return "ProfessionalPreparationTaskSnapshot[id=" + id + ", description=redacted, status="
                + status + "]";
    }
}
