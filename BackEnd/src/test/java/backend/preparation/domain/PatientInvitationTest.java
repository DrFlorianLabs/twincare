package backend.preparation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PatientInvitationTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-21T11:00:00Z");

    @Test
    void consumesAnActiveInvitationBeforeItsDeadline() {
        PatientInvitation invitation = invitation();

        invitation.consume(ISSUED_AT.plusSeconds(1));

        assertThat(invitation.status()).isEqualTo(PatientInvitationStatus.CONSUMED);
        assertThat(invitation.consumedAt()).contains(ISSUED_AT.plusSeconds(1));
        assertThat(invitation.updatedAt()).isEqualTo(ISSUED_AT.plusSeconds(1));
    }

    @Test
    void refusesConsumptionAtTheExactExpiryBoundary() {
        PatientInvitation invitation = invitation();

        assertThat(invitation.expiresAtOrBefore(EXPIRES_AT)).isTrue();
        assertThatThrownBy(() -> invitation.consume(EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(DIGEST.value());
    }

    @Test
    void revocationIsIdempotentAndPreservesPriorConsumptionEvidence() {
        PatientInvitation invitation = invitation();
        invitation.consume(ISSUED_AT.plusSeconds(1));

        invitation.revoke(ISSUED_AT.plusSeconds(2));
        invitation.revoke(ISSUED_AT.plusSeconds(3));

        assertThat(invitation.status()).isEqualTo(PatientInvitationStatus.REVOKED);
        assertThat(invitation.consumedAt()).contains(ISSUED_AT.plusSeconds(1));
        assertThat(invitation.revokedAt()).contains(ISSUED_AT.plusSeconds(2));
    }

    @Test
    void validatesRestoredStateAndRedactsCredentials() {
        assertThatThrownBy(() -> PatientInvitation.restore(
                ID,
                ORGANIZATION_ID,
                CASE_ID,
                42L,
                DIGEST,
                PatientInvitationStatus.CONSUMED,
                ISSUED_AT,
                EXPIRES_AT,
                Optional.empty(),
                Optional.empty(),
                ISSUED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(DIGEST.toString()).contains("<redacted>").doesNotContain(DIGEST.value());
        assertThat(TOKEN.toString()).contains("<redacted>").doesNotContain(TOKEN.value());
    }

    private PatientInvitation invitation() {
        return PatientInvitation.issue(
                ID,
                ORGANIZATION_ID,
                CASE_ID,
                42L,
                DIGEST,
                ISSUED_AT,
                EXPIRES_AT);
    }

    private static final PatientInvitationId ID = new PatientInvitationId(
            UUID.fromString("20aa48ee-b95e-4a57-91b7-f68a20462579"));
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("cabinet-a");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("8129dadf-a7b8-42e5-bfdc-01ab7787f405"));
    private static final SecureToken TOKEN = new SecureToken("A".repeat(SecureToken.ENCODED_LENGTH));
    private static final TokenDigest DIGEST = new TokenDigest("test-v1", "a".repeat(64));
}
