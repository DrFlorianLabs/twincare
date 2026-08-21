package backend.preparation.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import backend.preparation.domain.SecureToken;

class PatientTokenSecurityTest {

    @Test
    void generatesUrlSafeTokensWithAtLeast256BitsOfEntropy() {
        SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

        SecureToken first = generator.nextToken();
        SecureToken second = generator.nextToken();

        assertThat(first.value()).hasSize(SecureToken.ENCODED_LENGTH)
                .matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void separatesInvitationAndSessionDigestDomains() {
        HmacSha256TokenDigester digester = new HmacSha256TokenDigester(
                "synthetic-pepper-material-with-at-least-32-bytes",
                "test-v1");
        SecureToken token = new SecureToken("A".repeat(SecureToken.ENCODED_LENGTH));

        var invitation = digester.digestInvitation(token);
        var session = digester.digestSession(token);

        assertThat(invitation.value()).hasSize(64).isNotEqualTo(session.value());
        assertThat(invitation.keyId()).isEqualTo("test-v1");
    }

    @Test
    void refusesWeakPepperConfiguration() {
        assertThatThrownBy(() -> new HmacSha256TokenDigester("too-short", "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("too-short");
    }

    @Test
    void localExchangeGuardFailsClosedAtItsConfiguredLimit() {
        LocalPatientInvitationExchangeGuard guard = new LocalPatientInvitationExchangeGuard(2);
        var now = java.time.Instant.parse("2026-08-21T10:00:00Z");

        guard.requireAttemptAllowed(now);
        guard.requireAttemptAllowed(now.plusSeconds(1));

        assertThatThrownBy(() -> guard.requireAttemptAllowed(now.plusSeconds(2)))
                .isInstanceOf(backend.preparation.application.PatientInvitationUnavailableException.class);
        guard.requireAttemptAllowed(now.plusSeconds(62));
    }
}
