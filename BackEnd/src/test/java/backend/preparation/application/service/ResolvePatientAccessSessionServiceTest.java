package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.ResolvePatientAccessSessionCommand;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientAccessSessionScope;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.SecureToken;
import backend.preparation.domain.TokenDigest;

class ResolvePatientAccessSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final String RAW_TOKEN = "S".repeat(SecureToken.ENCODED_LENGTH);
    private static final TokenDigest DIGEST = new TokenDigest("test-v1", "a".repeat(64));

    @Test
    void resolvesAUsableSessionToOneServerOwnedGrant() {
        RecordingSessionRepository sessions = new RecordingSessionRepository(usableScope());
        ResolvePatientAccessSessionService service = service(sessions);

        var grant = service.resolve(new ResolvePatientAccessSessionCommand(RAW_TOKEN));

        assertThat(grant.sessionId()).isEqualTo(usableScope().sessionId());
        assertThat(grant.organizationId()).isEqualTo(usableScope().organizationId());
        assertThat(grant.preparationCaseId()).isEqualTo(usableScope().preparationCaseId());
        assertThat(grant.toString()).doesNotContain(grant.sessionId().toString());
    }

    @Test
    void malformedTokenStillPerformsADummyDigestLookupAndFailsClosed() {
        RecordingSessionRepository sessions = new RecordingSessionRepository(usableScope());
        ResolvePatientAccessSessionService service = service(sessions);

        assertThatThrownBy(() -> service.resolve(new ResolvePatientAccessSessionCommand("malformed")))
                .isInstanceOf(PatientSessionUnavailableException.class);
        assertThat(sessions.lookupCount).isOne();
    }

    @Test
    void exactExpiryRevocationWrongScopeInvitationAndCaseStateAllFailClosed() {
        assertUnavailable(scope(NOW.minusSeconds(60), NOW, Optional.empty(), PatientInvitationStatus.CONSUMED,
                PreparationCaseStatus.PATIENT_STARTED, PatientAccessSessionScope.CONTRIBUTION_SCOPE));
        assertUnavailable(scope(NOW.minusSeconds(60), NOW.plusSeconds(60), Optional.of(NOW.minusSeconds(1)),
                PatientInvitationStatus.CONSUMED,
                PreparationCaseStatus.PATIENT_STARTED, PatientAccessSessionScope.CONTRIBUTION_SCOPE));
        assertUnavailable(scope(NOW.minusSeconds(60), NOW.plusSeconds(60), Optional.empty(), PatientInvitationStatus.REVOKED,
                PreparationCaseStatus.PATIENT_STARTED, PatientAccessSessionScope.CONTRIBUTION_SCOPE));
        assertUnavailable(scope(NOW.minusSeconds(60), NOW.plusSeconds(60), Optional.empty(), PatientInvitationStatus.CONSUMED,
                PreparationCaseStatus.CLOSED, PatientAccessSessionScope.CONTRIBUTION_SCOPE));
        assertUnavailable(scope(NOW.minusSeconds(60), NOW.plusSeconds(60), Optional.empty(), PatientInvitationStatus.CONSUMED,
                PreparationCaseStatus.PATIENT_STARTED, "WRONG_SCOPE"));
        assertUnavailable(scope(NOW.plusSeconds(1), NOW.plusSeconds(60), Optional.empty(),
                PatientInvitationStatus.CONSUMED,
                PreparationCaseStatus.PATIENT_STARTED, PatientAccessSessionScope.CONTRIBUTION_SCOPE));
    }

    private void assertUnavailable(PatientAccessSessionScope scope) {
        var service = service(new RecordingSessionRepository(scope));
        assertThatThrownBy(() -> service.resolve(new ResolvePatientAccessSessionCommand(RAW_TOKEN)))
                .isInstanceOf(PatientSessionUnavailableException.class);
    }

    private ResolvePatientAccessSessionService service(RecordingSessionRepository sessions) {
        SecureTokenDigester digester = new SecureTokenDigester() {
            @Override
            public TokenDigest digestInvitation(SecureToken token) {
                return DIGEST;
            }

            @Override
            public TokenDigest digestSession(SecureToken token) {
                return DIGEST;
            }
        };
        return new ResolvePatientAccessSessionService(
                sessions, digester, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PatientAccessSessionScope usableScope() {
        return scope(
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                Optional.empty(),
                PatientInvitationStatus.CONSUMED,
                PreparationCaseStatus.PATIENT_STARTED,
                PatientAccessSessionScope.CONTRIBUTION_SCOPE);
    }

    private PatientAccessSessionScope scope(
            Instant issuedAt,
            Instant expiresAt,
            Optional<Instant> revokedAt,
            PatientInvitationStatus invitationStatus,
            PreparationCaseStatus caseStatus,
            String accessScope) {
        return new PatientAccessSessionScope(
                UUID.fromString("b2fd6687-3a6a-4f1e-b29f-959746390e0b"),
                new PatientInvitationId(UUID.fromString("a06a8793-970c-4ae8-912a-aef0816c8abf")),
                new OrganizationId("synthetic-clinic"),
                new PreparationCaseId(UUID.fromString("230fd7be-8869-466b-af6d-f96e61d26775")),
                accessScope,
                invitationStatus,
                caseStatus,
                issuedAt,
                expiresAt,
                revokedAt);
    }

    private static final class RecordingSessionRepository implements PatientAccessSessionRepository {
        private final PatientAccessSessionScope scope;
        private int lookupCount;

        private RecordingSessionRepository(PatientAccessSessionScope scope) {
            this.scope = scope;
        }

        @Override
        public Optional<PatientAccessSessionScope> findScopeByTokenDigest(TokenDigest tokenDigest) {
            lookupCount++;
            return DIGEST.equals(tokenDigest) ? Optional.of(scope) : Optional.empty();
        }

        @Override
        public Optional<PatientAccessSessionScope> findScopeByIdentity(
                UUID id,
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(PatientAccessSession session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PatientAccessSession> findByIdForUpdate(
                UUID id,
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(PatientAccessSession session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt) {
            throw new UnsupportedOperationException();
        }
    }
}
