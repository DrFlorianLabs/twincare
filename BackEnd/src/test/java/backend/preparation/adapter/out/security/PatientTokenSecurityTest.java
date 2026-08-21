package backend.preparation.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.SecureToken;

class PatientTokenSecurityTest {

    @Test
    void secureTokenAcceptsOnlyAsciiBase64UrlCharacters() {
        assertThatThrownBy(() -> new SecureToken("é".repeat(SecureToken.ENCODED_LENGTH)))
                .isInstanceOf(IllegalArgumentException.class);
    }

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
    void csrfTokenIsDeterministicAndBoundToOneSessionScope() {
        HmacPatientCsrfTokenIssuer issuer = new HmacPatientCsrfTokenIssuer(
                "synthetic-pepper-material-with-at-least-32-bytes");
        PatientAccessGrant firstGrant = grant(UUID.fromString("281b0d9d-30f1-4dc3-b61b-449d09b92de1"));
        PatientAccessGrant secondGrant = grant(UUID.fromString("1939f9b9-809f-43af-b96a-b745669db913"));

        SecureToken first = issuer.issueFor(firstGrant);

        assertThat(first.value()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issuer.issueFor(firstGrant)).isEqualTo(first);
        assertThat(issuer.issueFor(secondGrant)).isNotEqualTo(first);
    }

    @Test
    void refusesWeakPepperConfiguration() {
        assertThatThrownBy(() -> new HmacSha256TokenDigester("too-short", "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("too-short");
    }

    private PatientAccessGrant grant(UUID sessionId) {
        return new PatientAccessGrant(
                sessionId,
                new PatientInvitationId(UUID.fromString("c2408598-64d9-48c7-8811-4371015785df")),
                new OrganizationId("synthetic-clinic"),
                new PreparationCaseId(UUID.fromString("70018db3-5497-4215-bfa2-73ac0b3fb4a0")),
                Instant.parse("2026-08-21T11:00:00Z"));
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
