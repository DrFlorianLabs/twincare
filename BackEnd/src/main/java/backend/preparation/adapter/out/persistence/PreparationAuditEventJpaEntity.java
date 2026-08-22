package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PreparationAuditActorKind;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "preparation_audit_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_preparation_audit_case_sequence",
                columnNames = { "organization_id", "preparation_case_id", "event_sequence" }))
class PreparationAuditEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 128)
    private String organizationId;

    @Column(name = "preparation_case_id", nullable = false, updatable = false)
    private UUID preparationCaseId;

    @Column(name = "event_sequence", nullable = false, updatable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private PreparationAuditEventType eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private short eventVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, updatable = false, length = 16)
    private PreparationAuditActorKind actorKind;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "case_revision", nullable = false, updatable = false)
    private long caseRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", updatable = false, length = 32)
    private PreparationAuditSubjectType subjectType;

    @Column(name = "subject_id", updatable = false)
    private UUID subjectId;

    protected PreparationAuditEventJpaEntity() {
    }

    PreparationAuditEventJpaEntity(
            UUID eventId,
            String organizationId,
            UUID preparationCaseId,
            long sequence,
            PreparationAuditEventType eventType,
            int eventVersion,
            PreparationAuditActorKind actorKind,
            Long actorUserId,
            Instant occurredAt,
            long caseRevision,
            PreparationAuditSubjectType subjectType,
            UUID subjectId) {
        this.eventId = eventId;
        this.organizationId = organizationId;
        this.preparationCaseId = preparationCaseId;
        this.sequence = sequence;
        this.eventType = eventType;
        this.eventVersion = (short) eventVersion;
        this.actorKind = actorKind;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
        this.caseRevision = caseRevision;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
    }

    long sequence() { return sequence; }
    PreparationAuditEventType eventType() { return eventType; }
    int eventVersion() { return eventVersion; }
    PreparationAuditActorKind actorKind() { return actorKind; }
    Long actorUserId() { return actorUserId; }
    Instant occurredAt() { return occurredAt; }
    long caseRevision() { return caseRevision; }
    PreparationAuditSubjectType subjectType() { return subjectType; }
    UUID subjectId() { return subjectId; }
}
