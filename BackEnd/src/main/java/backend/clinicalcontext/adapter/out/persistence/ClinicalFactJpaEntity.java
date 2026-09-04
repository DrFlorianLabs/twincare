package backend.clinicalcontext.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.clinicalcontext.domain.ClinicalFactKind;
import backend.clinicalcontext.domain.FactCertainty;
import backend.clinicalcontext.domain.FactLifecycleStatus;
import backend.clinicalcontext.domain.FactReviewStatus;
import backend.clinicalcontext.domain.ProvenanceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "clinical_fact",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_clinical_fact_current_kind",
                columnNames = { "preparation_case_id", "organization_id", "fact_kind", "current_slot" }))
class ClinicalFactJpaEntity {

    @Id
    private UUID id;

    @Column(name = "preparation_case_id", nullable = false)
    private UUID contextId;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_kind", nullable = false, length = 48, columnDefinition = "VARCHAR(48)")
    private ClinicalFactKind kind;

    @Column(name = "text_value", nullable = false, length = 4000)
    private String textValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private ProvenanceType provenance;

    @Enumerated(EnumType.STRING)
    @Column(name = "certainty", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private FactCertainty certainty;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "effective_from", updatable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to", updatable = false)
    private Instant effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 24, columnDefinition = "VARCHAR(24)")
    private FactLifecycleStatus lifecycleStatus;

    @Column(name = "current_slot")
    private Short currentSlot;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 24, columnDefinition = "VARCHAR(24)")
    private FactReviewStatus reviewStatus;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewer_reference", length = 128)
    private String reviewerReference;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ClinicalFactJpaEntity() {
    }

    ClinicalFactJpaEntity(
            UUID id,
            UUID contextId,
            String ownerId,
            ClinicalFactKind kind,
            String textValue,
            ProvenanceType provenance,
            FactCertainty certainty,
            Instant recordedAt,
            Instant effectiveFrom,
            Instant effectiveTo,
            FactLifecycleStatus lifecycleStatus,
            Short currentSlot,
            Instant supersededAt,
            FactReviewStatus reviewStatus,
            Instant reviewedAt,
            String reviewerReference) {
        this.id = id;
        this.contextId = contextId;
        this.ownerId = ownerId;
        this.kind = kind;
        this.textValue = textValue;
        this.provenance = provenance;
        this.certainty = certainty;
        this.recordedAt = recordedAt;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.lifecycleStatus = lifecycleStatus;
        this.currentSlot = currentSlot;
        this.supersededAt = supersededAt;
        this.reviewStatus = reviewStatus;
        this.reviewedAt = reviewedAt;
        this.reviewerReference = reviewerReference;
    }

    UUID id() {
        return id;
    }

    UUID contextId() {
        return contextId;
    }

    String ownerId() {
        return ownerId;
    }

    ClinicalFactKind kind() {
        return kind;
    }

    String textValue() {
        return textValue;
    }

    ProvenanceType provenance() {
        return provenance;
    }

    FactCertainty certainty() {
        return certainty;
    }

    Instant recordedAt() {
        return recordedAt;
    }

    Instant effectiveFrom() {
        return effectiveFrom;
    }

    Instant effectiveTo() {
        return effectiveTo;
    }

    FactLifecycleStatus lifecycleStatus() {
        return lifecycleStatus;
    }

    Instant supersededAt() {
        return supersededAt;
    }

    FactReviewStatus reviewStatus() {
        return reviewStatus;
    }

    Instant reviewedAt() {
        return reviewedAt;
    }

    String reviewerReference() {
        return reviewerReference;
    }

    void updateFrom(ClinicalFactJpaEntity source) {
        if (!id.equals(source.id)
                || !contextId.equals(source.contextId)
                || !ownerId.equals(source.ownerId)
                || kind != source.kind
                || !textValue.equals(source.textValue)
                || provenance != source.provenance
                || certainty != source.certainty
                || !recordedAt.equals(source.recordedAt)
                || !java.util.Objects.equals(effectiveFrom, source.effectiveFrom)
                || !java.util.Objects.equals(effectiveTo, source.effectiveTo)) {
            throw new IllegalArgumentException("Clinical fact identity and assertion cannot change");
        }
        lifecycleStatus = source.lifecycleStatus;
        currentSlot = source.currentSlot;
        supersededAt = source.supersededAt;
        reviewStatus = source.reviewStatus;
        reviewedAt = source.reviewedAt;
        reviewerReference = source.reviewerReference;
    }
}
