package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PreparationTask {

    private final PreparationTaskId id;
    private final OrganizationId organizationId;
    private final PreparationCaseId preparationCaseId;
    private final UUID creationRequestId;
    private final PreparationTaskDescription description;
    private final long assigneeUserId;
    private final long createdByUserId;
    private final Instant createdAt;

    private PreparationTaskStatus status;
    private Optional<Long> closedByUserId;
    private Optional<Instant> closedAt;
    private Instant updatedAt;

    private PreparationTask(
            PreparationTaskId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            UUID creationRequestId,
            PreparationTaskDescription description,
            long assigneeUserId,
            long createdByUserId,
            Instant createdAt,
            PreparationTaskStatus status,
            Optional<Long> closedByUserId,
            Optional<Instant> closedAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Preparation task identifier is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization identifier is required");
        this.preparationCaseId = Objects.requireNonNull(
                preparationCaseId, "Preparation case identifier is required");
        this.creationRequestId = Objects.requireNonNull(creationRequestId, "Creation request identifier is required");
        this.description = Objects.requireNonNull(description, "Preparation task description is required");
        if (assigneeUserId <= 0) {
            throw new IllegalArgumentException("Preparation task assignee is invalid");
        }
        this.assigneeUserId = assigneeUserId;
        if (createdByUserId <= 0) {
            throw new IllegalArgumentException("Preparation task creator is invalid");
        }
        this.createdByUserId = createdByUserId;
        this.createdAt = Objects.requireNonNull(createdAt, "Preparation task creation timestamp is required");
        this.status = Objects.requireNonNull(status, "Preparation task status is required");
        this.closedByUserId = Objects.requireNonNull(closedByUserId, "Closer container is required");
        this.closedAt = Objects.requireNonNull(closedAt, "Closure timestamp container is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Preparation task update timestamp is required");
        validateState();
    }

    public static PreparationTask create(
            PreparationTaskId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            UUID creationRequestId,
            PreparationTaskDescription description,
            long assigneeUserId,
            long createdByUserId,
            Instant createdAt) {
        return new PreparationTask(
                id,
                organizationId,
                preparationCaseId,
                creationRequestId,
                description,
                assigneeUserId,
                createdByUserId,
                createdAt,
                PreparationTaskStatus.OPEN,
                Optional.empty(),
                Optional.empty(),
                createdAt);
    }

    public static PreparationTask restore(
            PreparationTaskId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            UUID creationRequestId,
            PreparationTaskDescription description,
            long assigneeUserId,
            long createdByUserId,
            Instant createdAt,
            PreparationTaskStatus status,
            Optional<Long> closedByUserId,
            Optional<Instant> closedAt,
            Instant updatedAt) {
        return new PreparationTask(
                id,
                organizationId,
                preparationCaseId,
                creationRequestId,
                description,
                assigneeUserId,
                createdByUserId,
                createdAt,
                status,
                closedByUserId,
                closedAt,
                updatedAt);
    }

    public boolean close(PreparationTaskStatus target, long actorUserId, Instant occurredAt) {
        if (target == null || !target.isTerminal()) {
            throw new IllegalArgumentException("Preparation task closure status is invalid");
        }
        if (actorUserId <= 0) {
            throw new IllegalArgumentException("Preparation task closer is invalid");
        }
        Objects.requireNonNull(occurredAt, "Preparation task closure timestamp is required");
        if (status == target) {
            return false;
        }
        if (status.isTerminal() || occurredAt.isBefore(updatedAt)) {
            throw new IllegalStateException("Preparation task cannot change to the requested state");
        }
        status = target;
        closedByUserId = Optional.of(actorUserId);
        closedAt = Optional.of(occurredAt);
        updatedAt = occurredAt;
        return true;
    }

    private void validateState() {
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Preparation task timeline is invalid");
        }
        boolean closedEvidenceComplete = closedByUserId.filter(value -> value > 0).isPresent()
                && closedAt.isPresent();
        if (status == PreparationTaskStatus.OPEN) {
            if (closedByUserId.isPresent() || closedAt.isPresent() || !updatedAt.equals(createdAt)) {
                throw new IllegalArgumentException("Open preparation task has closure evidence");
            }
        } else if (!closedEvidenceComplete
                || closedAt.filter(value -> value.isBefore(createdAt) || !value.equals(updatedAt)).isPresent()) {
            throw new IllegalArgumentException("Closed preparation task evidence is invalid");
        }
    }

    public PreparationTaskId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public PreparationCaseId preparationCaseId() {
        return preparationCaseId;
    }

    public PreparationTaskDescription description() {
        return description;
    }

    public UUID creationRequestId() {
        return creationRequestId;
    }

    public long assigneeUserId() {
        return assigneeUserId;
    }

    public long createdByUserId() {
        return createdByUserId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public PreparationTaskStatus status() {
        return status;
    }

    public Optional<Long> closedByUserId() {
        return closedByUserId;
    }

    public Optional<Instant> closedAt() {
        return closedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "PreparationTask[id=" + id + ", organizationId=" + organizationId
                + ", preparationCaseId=" + preparationCaseId + ", description=redacted, status="
                + status + "]";
    }
}
