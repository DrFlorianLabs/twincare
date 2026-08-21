package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PreparationTaskStatus;

interface ProfessionalPreparationTaskProjection {

    UUID getTaskId();

    String getDescription();

    Long getAssigneeUserId();

    PreparationTaskStatus getStatus();

    Long getCreatedByUserId();

    Instant getCreatedAt();

    Long getClosedByUserId();

    Instant getClosedAt();

    Instant getUpdatedAt();
}
