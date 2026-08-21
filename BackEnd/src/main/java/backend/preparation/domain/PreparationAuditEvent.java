package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record PreparationAuditEvent(
        UUID id,
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        PreparationAuditEventType type,
        int eventVersion,
        PreparationAuditActorKind actorKind,
        OptionalLong actorUserId,
        Instant occurredAt,
        long caseRevision,
        Optional<PreparationAuditSubject> subject) {

    public static final int CURRENT_EVENT_VERSION = 1;

    public PreparationAuditEvent {
        Objects.requireNonNull(id, "Audit event identifier is required");
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(type, "Audit event type is required");
        Objects.requireNonNull(actorKind, "Audit actor kind is required");
        Objects.requireNonNull(actorUserId, "Audit actor identifier container is required");
        Objects.requireNonNull(occurredAt, "Audit event timestamp is required");
        Objects.requireNonNull(subject, "Audit subject container is required");
        if (eventVersion != CURRENT_EVENT_VERSION || caseRevision < 0) {
            throw new IllegalArgumentException("Audit event version or case revision is invalid");
        }
        if ((actorKind == PreparationAuditActorKind.STAFF) != actorUserId.isPresent()
                || actorUserId.isPresent() && actorUserId.getAsLong() <= 0) {
            throw new IllegalArgumentException("Audit actor evidence is inconsistent");
        }
        validateEventSemantics(type, actorKind, subject);
    }

    private static void validateEventSemantics(
            PreparationAuditEventType type,
            PreparationAuditActorKind actorKind,
            Optional<PreparationAuditSubject> subject) {
        boolean valid = switch (type) {
            case PREPARATION_CREATED, READY_CONFIRMED ->
                    actorKind == PreparationAuditActorKind.STAFF && subject.isEmpty();
            case INVITATION_ISSUED ->
                    actorKind == PreparationAuditActorKind.STAFF && hasSubject(subject, PreparationAuditSubjectType.INVITATION);
            case INVITATION_REVOKED ->
                    (actorKind == PreparationAuditActorKind.STAFF || actorKind == PreparationAuditActorKind.SYSTEM)
                            && hasSubject(subject, PreparationAuditSubjectType.INVITATION);
            case INVITATION_EXPIRED ->
                    actorKind == PreparationAuditActorKind.SYSTEM
                            && hasSubject(subject, PreparationAuditSubjectType.INVITATION);
            case PATIENT_ACCESS_STARTED, PATIENT_ACCESS_ENDED, PATIENT_REASON_RECORDED ->
                    actorKind == PreparationAuditActorKind.PATIENT && subject.isEmpty();
            case PATIENT_ACCESS_REVOKED ->
                    (actorKind == PreparationAuditActorKind.STAFF || actorKind == PreparationAuditActorKind.SYSTEM)
                            && subject.isEmpty();
            case ATTACHMENT_CREATED, ATTACHMENT_REPLACED, ATTACHMENT_DELETED ->
                    actorKind == PreparationAuditActorKind.PATIENT
                            && hasSubject(subject, PreparationAuditSubjectType.ATTACHMENT);
            case ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED ->
                    actorKind == PreparationAuditActorKind.STAFF
                            && hasSubject(subject, PreparationAuditSubjectType.ATTACHMENT);
            case TASK_CREATED, TASK_COMPLETED, TASK_CANCELLED ->
                    actorKind == PreparationAuditActorKind.STAFF
                            && hasSubject(subject, PreparationAuditSubjectType.PREPARATION_TASK);
        };
        if (!valid) {
            throw new IllegalArgumentException("Audit event actor or subject is inconsistent");
        }
    }

    private static boolean hasSubject(
            Optional<PreparationAuditSubject> subject,
            PreparationAuditSubjectType expectedType) {
        return subject.filter(value -> value.type() == expectedType).isPresent();
    }

    public static PreparationAuditEvent staff(
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            PreparationAuditEventType type,
            long actorUserId,
            Instant occurredAt,
            long caseRevision,
            Optional<PreparationAuditSubject> subject) {
        return new PreparationAuditEvent(
                UUID.randomUUID(), organizationId, preparationCaseId, type, CURRENT_EVENT_VERSION,
                PreparationAuditActorKind.STAFF, OptionalLong.of(actorUserId), occurredAt, caseRevision, subject);
    }

    public static PreparationAuditEvent patient(
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            PreparationAuditEventType type,
            Instant occurredAt,
            long caseRevision,
            Optional<PreparationAuditSubject> subject) {
        return new PreparationAuditEvent(
                UUID.randomUUID(), organizationId, preparationCaseId, type, CURRENT_EVENT_VERSION,
                PreparationAuditActorKind.PATIENT, OptionalLong.empty(), occurredAt, caseRevision, subject);
    }

    public static PreparationAuditEvent system(
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            PreparationAuditEventType type,
            Instant occurredAt,
            long caseRevision,
            Optional<PreparationAuditSubject> subject) {
        return new PreparationAuditEvent(
                UUID.randomUUID(), organizationId, preparationCaseId, type, CURRENT_EVENT_VERSION,
                PreparationAuditActorKind.SYSTEM, OptionalLong.empty(), occurredAt, caseRevision, subject);
    }

    @Override
    public String toString() {
        return "PreparationAuditEvent[metadata redacted]";
    }
}
