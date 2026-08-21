package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PreparationTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "preparation_task")
class PreparationTaskJpaEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "preparation_case_id", nullable = false)
    private UUID preparationCaseId;

    @Column(name = "creation_request_id", nullable = false, updatable = false)
    private UUID creationRequestId;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(name = "assignee_user_id", nullable = false)
    private Long assigneeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private PreparationTaskStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PreparationTaskJpaEntity() {
    }

    PreparationTaskJpaEntity(
            UUID id,
            String organizationId,
            UUID preparationCaseId,
            UUID creationRequestId,
            String description,
            Long assigneeUserId,
            PreparationTaskStatus status,
            Long createdByUserId,
            Instant createdAt,
            Long closedByUserId,
            Instant closedAt,
            Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.preparationCaseId = preparationCaseId;
        this.creationRequestId = creationRequestId;
        this.description = description;
        this.assigneeUserId = assigneeUserId;
        this.status = status;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.closedByUserId = closedByUserId;
        this.closedAt = closedAt;
        this.updatedAt = updatedAt;
    }

    UUID id() {
        return id;
    }

    String organizationId() {
        return organizationId;
    }

    UUID preparationCaseId() {
        return preparationCaseId;
    }

    UUID creationRequestId() {
        return creationRequestId;
    }

    String description() {
        return description;
    }

    Long assigneeUserId() {
        return assigneeUserId;
    }

    PreparationTaskStatus status() {
        return status;
    }

    Long createdByUserId() {
        return createdByUserId;
    }

    Instant createdAt() {
        return createdAt;
    }

    Long closedByUserId() {
        return closedByUserId;
    }

    Instant closedAt() {
        return closedAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void updateFrom(PreparationTaskJpaEntity source) {
        if (!id.equals(source.id)
                || !organizationId.equals(source.organizationId)
                || !preparationCaseId.equals(source.preparationCaseId)) {
            throw new IllegalArgumentException("Preparation task identity cannot change");
        }
        status = source.status;
        closedByUserId = source.closedByUserId;
        closedAt = source.closedAt;
        updatedAt = source.updatedAt;
    }
}
