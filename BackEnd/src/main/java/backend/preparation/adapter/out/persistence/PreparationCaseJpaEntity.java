package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.ProvenanceType;
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
        name = "preparation_case",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_preparation_case_appointment",
                        columnNames = { "organization_id", "appointment_reference" }),
                @UniqueConstraint(
                        name = "uq_preparation_case_patient_schedule",
                        columnNames = { "organization_id", "patient_reference", "scheduled_at" }),
                @UniqueConstraint(
                        name = "uq_preparation_case_creation_request",
                        columnNames = { "organization_id", "creation_request_id" })
        })
class PreparationCaseJpaEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "patient_reference", nullable = false, length = 128)
    private String patientReference;

    @Column(name = "appointment_reference", length = 128)
    private String appointmentReference;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "creation_request_id", updatable = false)
    private UUID creationRequestId;

    @Column(name = "creation_request_fingerprint", updatable = false, length = 64)
    private String creationRequestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private PreparationCaseStatus status;

    @Column(name = "patient_reason", length = 4000)
    private String patientReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_reason_provenance", length = 40, columnDefinition = "VARCHAR(40)")
    private ProvenanceType patientReasonProvenance;

    @Column(name = "patient_reason_recorded_at")
    private Instant patientReasonRecordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PreparationCaseJpaEntity() {
    }

    PreparationCaseJpaEntity(
            UUID id,
            String organizationId,
            String patientReference,
            String appointmentReference,
            Instant scheduledAt,
            UUID creationRequestId,
            String creationRequestFingerprint,
            PreparationCaseStatus status,
            String patientReason,
            ProvenanceType patientReasonProvenance,
            Instant patientReasonRecordedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.patientReference = patientReference;
        this.appointmentReference = appointmentReference;
        this.scheduledAt = scheduledAt;
        this.creationRequestId = creationRequestId;
        this.creationRequestFingerprint = creationRequestFingerprint;
        this.status = status;
        this.patientReason = patientReason;
        this.patientReasonProvenance = patientReasonProvenance;
        this.patientReasonRecordedAt = patientReasonRecordedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID id() {
        return id;
    }

    String organizationId() {
        return organizationId;
    }

    String patientReference() {
        return patientReference;
    }

    String appointmentReference() {
        return appointmentReference;
    }

    Instant scheduledAt() {
        return scheduledAt;
    }

    UUID creationRequestId() {
        return creationRequestId;
    }

    String creationRequestFingerprint() {
        return creationRequestFingerprint;
    }

    PreparationCaseStatus status() {
        return status;
    }

    String patientReason() {
        return patientReason;
    }

    ProvenanceType patientReasonProvenance() {
        return patientReasonProvenance;
    }

    Instant patientReasonRecordedAt() {
        return patientReasonRecordedAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    Long version() {
        return version;
    }

    void updateFrom(PreparationCaseJpaEntity source) {
        if (!id.equals(source.id) || !organizationId.equals(source.organizationId)) {
            throw new IllegalArgumentException("Preparation case identity cannot change");
        }
        patientReference = source.patientReference;
        appointmentReference = source.appointmentReference;
        scheduledAt = source.scheduledAt;
        status = source.status;
        patientReason = source.patientReason;
        patientReasonProvenance = source.patientReasonProvenance;
        patientReasonRecordedAt = source.patientReasonRecordedAt;
        updatedAt = source.updatedAt;
    }
}
