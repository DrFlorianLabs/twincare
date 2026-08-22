package backend.preparation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PreparationAuditEventTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void staffActorRequiresExactlyOnePositiveServerIdentity() {
        assertThatThrownBy(() -> new PreparationAuditEvent(
                UUID.randomUUID(), ORGANIZATION_ID, CASE_ID,
                PreparationAuditEventType.TASK_CREATED, 1,
                PreparationAuditActorKind.STAFF, OptionalLong.empty(), NOW, 0, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationAuditEvent(
                UUID.randomUUID(), ORGANIZATION_ID, CASE_ID,
                PreparationAuditEventType.TASK_CREATED, 1,
                PreparationAuditActorKind.PATIENT, OptionalLong.of(42), NOW, 0, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownSchemaVersionAndNegativeRevision() {
        assertThatThrownBy(() -> new PreparationAuditEvent(
                UUID.randomUUID(), ORGANIZATION_ID, CASE_ID,
                PreparationAuditEventType.PREPARATION_CREATED, 2,
                PreparationAuditActorKind.SYSTEM, OptionalLong.empty(), NOW, 0, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationAuditEvent(
                UUID.randomUUID(), ORGANIZATION_ID, CASE_ID,
                PreparationAuditEventType.PREPARATION_CREATED, 1,
                PreparationAuditActorKind.SYSTEM, OptionalLong.empty(), NOW, -1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringRepresentationNeverExposesAuditMetadata() {
        var event = PreparationAuditEvent.staff(
                ORGANIZATION_ID, CASE_ID, PreparationAuditEventType.READY_CONFIRMED,
                42, NOW, 7, Optional.empty());

        assertThat(event.toString()).isEqualTo("PreparationAuditEvent[metadata redacted]")
                .doesNotContain(ORGANIZATION_ID.value(), CASE_ID.value().toString(), "42");
    }

    @Test
    void professionalAccessRevocationHasAStaffActorAndNoSessionIdentifier() {
        var event = PreparationAuditEvent.staff(
                ORGANIZATION_ID, CASE_ID, PreparationAuditEventType.PATIENT_ACCESS_REVOKED,
                42, NOW, 7, Optional.empty());

        assertThat(event.actorKind()).isEqualTo(PreparationAuditActorKind.STAFF);
        assertThat(event.subject()).isEmpty();
        assertThatThrownBy(() -> PreparationAuditEvent.patient(
                ORGANIZATION_ID, CASE_ID, PreparationAuditEventType.PATIENT_ACCESS_REVOKED,
                NOW, 7, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preparationClosureIsStaffOnlyAndContainsNoClinicalSubject() {
        var event = PreparationAuditEvent.staff(
                ORGANIZATION_ID, CASE_ID, PreparationAuditEventType.PREPARATION_CLOSED,
                42, NOW, 9, Optional.empty());

        assertThat(event.actorKind()).isEqualTo(PreparationAuditActorKind.STAFF);
        assertThat(event.subject()).isEmpty();
        assertThatThrownBy(() -> PreparationAuditEvent.patient(
                ORGANIZATION_ID, CASE_ID, PreparationAuditEventType.PREPARATION_CLOSED,
                NOW, 9, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
