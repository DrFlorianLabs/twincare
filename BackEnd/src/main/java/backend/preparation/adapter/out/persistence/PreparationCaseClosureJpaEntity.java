package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PreparationCaseClosure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "preparation_case_closure")
class PreparationCaseClosureJpaEntity {

    @Id
    @Column(name = "preparation_case_id")
    private UUID preparationCaseId;

    @Column(name = "organization_id", nullable = false, length = 128, updatable = false)
    private String organizationId;

    @Column(name = "closed_by_user_id", nullable = false, updatable = false)
    private Long closedByUserId;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private Instant closedAt;

    @Column(name = "ready_case_revision", nullable = false, updatable = false)
    private Long readyCaseRevision;

    @Column(name = "closed_case_revision", nullable = false, updatable = false)
    private Long closedCaseRevision;

    protected PreparationCaseClosureJpaEntity() {
    }

    PreparationCaseClosureJpaEntity(PreparationCaseClosure closure) {
        this.preparationCaseId = closure.preparationCaseId().value();
        this.organizationId = closure.organizationId().value();
        this.closedByUserId = closure.closedByUserId();
        this.closedAt = closure.closedAt();
        this.readyCaseRevision = closure.readyCaseRevision();
        this.closedCaseRevision = closure.closedCaseRevision();
    }

    UUID preparationCaseId() {
        return preparationCaseId;
    }

    String organizationId() {
        return organizationId;
    }

    Long closedByUserId() {
        return closedByUserId;
    }

    Instant closedAt() {
        return closedAt;
    }

    Long readyCaseRevision() {
        return readyCaseRevision;
    }

    Long closedCaseRevision() {
        return closedCaseRevision;
    }
}
