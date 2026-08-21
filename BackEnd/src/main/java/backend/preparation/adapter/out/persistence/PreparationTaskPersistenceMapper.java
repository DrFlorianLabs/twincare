package backend.preparation.adapter.out.persistence;

import java.util.Optional;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationTask;
import backend.preparation.domain.PreparationTaskDescription;
import backend.preparation.domain.PreparationTaskId;

final class PreparationTaskPersistenceMapper {

    PreparationTaskJpaEntity toEntity(PreparationTask task) {
        return new PreparationTaskJpaEntity(
                task.id().value(),
                task.organizationId().value(),
                task.preparationCaseId().value(),
                task.creationRequestId(),
                task.description().value(),
                task.assigneeUserId(),
                task.status(),
                task.createdByUserId(),
                task.createdAt(),
                task.closedByUserId().orElse(null),
                task.closedAt().orElse(null),
                task.updatedAt());
    }

    PreparationTask toDomain(PreparationTaskJpaEntity entity) {
        return PreparationTask.restore(
                new PreparationTaskId(entity.id()),
                new OrganizationId(entity.organizationId()),
                new PreparationCaseId(entity.preparationCaseId()),
                entity.creationRequestId(),
                new PreparationTaskDescription(entity.description()),
                entity.assigneeUserId(),
                entity.createdByUserId(),
                entity.createdAt(),
                entity.status(),
                Optional.ofNullable(entity.closedByUserId()),
                Optional.ofNullable(entity.closedAt()),
                entity.updatedAt());
    }
}
