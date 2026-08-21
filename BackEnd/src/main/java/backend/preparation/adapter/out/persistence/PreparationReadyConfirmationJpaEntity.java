package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "preparation_ready_confirmation")
class PreparationReadyConfirmationJpaEntity {

    @Id
    @Column(name = "preparation_case_id")
    private UUID preparationCaseId;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "confirmed_by_user_id", nullable = false)
    private Long confirmedByUserId;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    @Column(name = "rule_set_id", nullable = false, length = 64, updatable = false)
    private String ruleSetId;

    @Column(name = "rule_set_version", nullable = false, updatable = false)
    private Integer ruleSetVersion;

    @Column(name = "reviewed_case_revision", nullable = false, updatable = false)
    private Long reviewedCaseRevision;

    @Column(name = "reviewed_reason_recorded_at", nullable = false, updatable = false)
    private Instant reviewedReasonRecordedAt;

    @Column(name = "open_task_count", nullable = false, updatable = false)
    private Integer openTaskCount;

    protected PreparationReadyConfirmationJpaEntity() {
    }

    PreparationReadyConfirmationJpaEntity(
            UUID preparationCaseId,
            String organizationId,
            Long confirmedByUserId,
            Instant confirmedAt,
            String ruleSetId,
            Integer ruleSetVersion,
            Long reviewedCaseRevision,
            Instant reviewedReasonRecordedAt,
            Integer openTaskCount) {
        this.preparationCaseId = preparationCaseId;
        this.organizationId = organizationId;
        this.confirmedByUserId = confirmedByUserId;
        this.confirmedAt = confirmedAt;
        this.ruleSetId = ruleSetId;
        this.ruleSetVersion = ruleSetVersion;
        this.reviewedCaseRevision = reviewedCaseRevision;
        this.reviewedReasonRecordedAt = reviewedReasonRecordedAt;
        this.openTaskCount = openTaskCount;
    }

    UUID preparationCaseId() {
        return preparationCaseId;
    }

    String organizationId() {
        return organizationId;
    }

    Long confirmedByUserId() {
        return confirmedByUserId;
    }

    Instant confirmedAt() {
        return confirmedAt;
    }

    String ruleSetId() {
        return ruleSetId;
    }

    Integer ruleSetVersion() {
        return ruleSetVersion;
    }

    Long reviewedCaseRevision() {
        return reviewedCaseRevision;
    }

    Instant reviewedReasonRecordedAt() {
        return reviewedReasonRecordedAt;
    }

    Integer openTaskCount() {
        return openTaskCount;
    }
}
