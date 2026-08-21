package backend.preparation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PatientAccessSessionTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void sessionIsActiveOnlyInsideItsUnrevokedExclusiveTimeWindow() {
        PatientAccessSession session = newSession();

        assertThat(session.isActiveAt(ISSUED_AT.minusNanos(1))).isFalse();
        assertThat(session.isActiveAt(ISSUED_AT)).isTrue();
        assertThat(session.isActiveAt(ISSUED_AT.plusSeconds(3599))).isTrue();
        assertThat(session.isActiveAt(ISSUED_AT.plusSeconds(3600))).isFalse();
    }

    @Test
    void revocationInvalidatesSessionImmediately() {
        PatientAccessSession session = newSession();

        session.revoke(ISSUED_AT.plusSeconds(60));

        assertThat(session.isActiveAt(ISSUED_AT.plusSeconds(60))).isFalse();
    }

    private PatientAccessSession newSession() {
        PatientInvitation invitation = PatientInvitation.issue(
                new PatientInvitationId(UUID.randomUUID()),
                new OrganizationId("synthetic-clinic"),
                new PreparationCaseId(UUID.randomUUID()),
                42L,
                new TokenDigest("test-v1", "a".repeat(64)),
                ISSUED_AT,
                ISSUED_AT.plusSeconds(3600));
        return PatientAccessSession.issue(
                UUID.randomUUID(),
                invitation,
                new TokenDigest("test-v1", "b".repeat(64)),
                ISSUED_AT);
    }
}
