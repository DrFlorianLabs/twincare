package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.exception.UnauthorizedActionException;
import backend.preparation.application.PatientInvitationUnavailableException;
import backend.preparation.application.port.in.ExchangePatientInvitationCommand;
import backend.preparation.application.port.in.IssuePatientInvitationCommand;
import backend.preparation.application.port.in.RevokePatientInvitationCommand;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PatientInvitationScope;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.SecureToken;
import backend.preparation.domain.TokenDigest;

class PatientInvitationServicesTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("cabinet-a");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("8129dadf-a7b8-42e5-bfdc-01ab7787f405"));
    private static final PatientInvitationId INVITATION_ID = new PatientInvitationId(
            UUID.fromString("20aa48ee-b95e-4a57-91b7-f68a20462579"));
    private static final SecureToken INVITATION_TOKEN = new SecureToken("A".repeat(43));
    private static final SecureToken SESSION_TOKEN = new SecureToken("B".repeat(43));
    private static final SecureTokenDigester DIGESTER = new TestDigester();

    @Test
    void issuesABoundedInvitationAndRevokesThePreviousActiveCredential() {
        RecordingPreparationCaseRepository cases = new RecordingPreparationCaseRepository(newCase());
        RecordingInvitationRepository invitations = new RecordingInvitationRepository();
        PatientInvitation previous = PatientInvitation.issue(
                new PatientInvitationId(UUID.randomUUID()),
                ORGANIZATION_ID,
                CASE_ID,
                41L,
                new TokenDigest("test-v1", "c".repeat(64)),
                NOW.minusSeconds(60),
                NOW.plusSeconds(7200));
        invitations.current = previous;
        RecordingAccessSessionRepository sessions = new RecordingAccessSessionRepository();
        IssuePatientInvitationService service = new IssuePatientInvitationService(
                organizationId -> 42L,
                cases,
                invitations,
                sessions,
                () -> INVITATION_ID,
                () -> INVITATION_TOKEN,
                DIGESTER,
                fixedClock(),
                Duration.ofHours(72));

        var result = service.issue(new IssuePatientInvitationCommand(ORGANIZATION_ID, CASE_ID));

        assertThat(previous.status()).isEqualTo(PatientInvitationStatus.REVOKED);
        assertThat(sessions.revokedInvitationIds).containsExactly(previous.id());
        assertThat(invitations.added).isNotNull();
        assertThat(invitations.added.issuedByUserId()).isEqualTo(42L);
        assertThat(result.token()).isEqualTo(INVITATION_TOKEN);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void authorizationFailureStopsBeforeCaseLookupOrSecretGeneration() {
        RecordingPreparationCaseRepository cases = new RecordingPreparationCaseRepository(newCase());
        boolean[] generated = { false };
        PreparationCaseAuthorization denied = organizationId -> {
            throw new UnauthorizedActionException("forbidden");
        };
        IssuePatientInvitationService service = new IssuePatientInvitationService(
                denied,
                cases,
                new RecordingInvitationRepository(),
                new RecordingAccessSessionRepository(),
                () -> INVITATION_ID,
                () -> {
                    generated[0] = true;
                    return INVITATION_TOKEN;
                },
                DIGESTER,
                fixedClock(),
                Duration.ofHours(1));

        assertThatThrownBy(() -> service.issue(new IssuePatientInvitationCommand(ORGANIZATION_ID, CASE_ID)))
                .isInstanceOf(UnauthorizedActionException.class);
        assertThat(cases.lockedLookups).isZero();
        assertThat(generated[0]).isFalse();
    }

    @Test
    void exchangesExactlyOnceIntoAScopedSessionAndStartsTheCase() {
        PreparationCase preparationCase = newCase();
        RecordingPreparationCaseRepository cases = new RecordingPreparationCaseRepository(preparationCase);
        RecordingInvitationRepository invitations = new RecordingInvitationRepository();
        invitations.current = invitation(NOW.minusSeconds(60), NOW.plusSeconds(3600));
        RecordingAccessSessionRepository sessions = new RecordingAccessSessionRepository();
        ExchangePatientInvitationService service = new ExchangePatientInvitationService(
                invitations,
                cases,
                sessions,
                () -> UUID.fromString("eb5eb31a-d811-456c-9be6-83ab9113b936"),
                () -> SESSION_TOKEN,
                DIGESTER,
                occurredAt -> {
                },
                fixedClock());

        var result = service.exchange(new ExchangePatientInvitationCommand(INVITATION_TOKEN.value()));

        assertThat(result.sessionToken()).isEqualTo(SESSION_TOKEN);
        assertThat(sessions.added).isNotNull();
        assertThat(sessions.added.preparationCaseId()).isEqualTo(CASE_ID);
        assertThat(invitations.current.status()).isEqualTo(PatientInvitationStatus.CONSUMED);
        assertThat(cases.current.status()).isEqualTo(PreparationCaseStatus.PATIENT_STARTED);

        assertThatThrownBy(() -> service.exchange(new ExchangePatientInvitationCommand(INVITATION_TOKEN.value())))
                .isInstanceOf(PatientInvitationUnavailableException.class)
                .hasMessageNotContaining(INVITATION_TOKEN.value());
    }

    @Test
    void expiryBoundaryFailsClosedAndPersistsExpiryState() {
        RecordingPreparationCaseRepository cases = new RecordingPreparationCaseRepository(newCase());
        RecordingInvitationRepository invitations = new RecordingInvitationRepository();
        invitations.current = invitation(NOW.minusSeconds(60), NOW);
        ExchangePatientInvitationService service = new ExchangePatientInvitationService(
                invitations,
                cases,
                new RecordingAccessSessionRepository(),
                UUID::randomUUID,
                () -> SESSION_TOKEN,
                DIGESTER,
                occurredAt -> {
                },
                fixedClock());

        assertThatThrownBy(() -> service.exchange(new ExchangePatientInvitationCommand(INVITATION_TOKEN.value())))
                .isInstanceOf(PatientInvitationUnavailableException.class);
        assertThat(invitations.current.status()).isEqualTo(PatientInvitationStatus.EXPIRED);
        assertThat(cases.current.status()).isEqualTo(PreparationCaseStatus.CREATED);
    }

    @Test
    void malformedSecretsStillExerciseTheDigestLookupAndReturnTheGenericFailure() {
        RecordingInvitationRepository invitations = new RecordingInvitationRepository();
        ExchangePatientInvitationService service = new ExchangePatientInvitationService(
                invitations,
                new RecordingPreparationCaseRepository(newCase()),
                new RecordingAccessSessionRepository(),
                UUID::randomUUID,
                () -> SESSION_TOKEN,
                DIGESTER,
                occurredAt -> {
                },
                fixedClock());

        assertThatThrownBy(() -> service.exchange(new ExchangePatientInvitationCommand("malformed")))
                .isInstanceOf(PatientInvitationUnavailableException.class)
                .hasMessage("Patient invitation is unavailable");
        assertThat(invitations.digestLookups).isOne();
    }

    @Test
    void professionalRevocationAlsoRevokesAnIssuedSessionAndIsIdempotent() {
        RecordingInvitationRepository invitations = new RecordingInvitationRepository();
        PatientInvitation consumed = invitation(NOW.minusSeconds(60), NOW.plusSeconds(3600));
        consumed.consume(NOW.minusSeconds(30));
        invitations.current = consumed;
        RecordingAccessSessionRepository sessions = new RecordingAccessSessionRepository();
        RevokePatientInvitationService service = new RevokePatientInvitationService(
                organizationId -> 42L,
                new RecordingPreparationCaseRepository(newCase()),
                invitations,
                sessions,
                fixedClock());
        var command = new RevokePatientInvitationCommand(ORGANIZATION_ID, CASE_ID, INVITATION_ID);

        service.revoke(command);
        service.revoke(command);

        assertThat(invitations.current.status()).isEqualTo(PatientInvitationStatus.REVOKED);
        assertThat(invitations.current.consumedAt()).contains(NOW.minusSeconds(30));
        assertThat(sessions.revokedInvitationIds).containsExactly(INVITATION_ID, INVITATION_ID);
    }

    private PreparationCase newCase() {
        return PreparationCase.create(
                CASE_ID,
                ORGANIZATION_ID,
                new PatientReference("synthetic-patient"),
                Optional.of(new AppointmentReference("synthetic-appointment")),
                Optional.of(NOW.plusSeconds(3600)),
                NOW.minusSeconds(3600));
    }

    private PatientInvitation invitation(Instant issuedAt, Instant expiresAt) {
        return PatientInvitation.issue(
                INVITATION_ID,
                ORGANIZATION_ID,
                CASE_ID,
                42L,
                DIGESTER.digestInvitation(INVITATION_TOKEN),
                issuedAt,
                expiresAt);
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static final class TestDigester implements SecureTokenDigester {
        @Override
        public TokenDigest digestInvitation(SecureToken token) {
            return new TokenDigest("test-v1", token.equals(INVITATION_TOKEN) ? "a".repeat(64) : "c".repeat(64));
        }

        @Override
        public TokenDigest digestSession(SecureToken token) {
            return new TokenDigest("test-v1", "b".repeat(64));
        }
    }

    private static final class RecordingPreparationCaseRepository implements PreparationCaseRepository {
        private PreparationCase current;
        private int lockedLookups;

        private RecordingPreparationCaseRepository(PreparationCase current) {
            this.current = current;
        }

        @Override
        public void add(PreparationCase preparationCase) {
            current = preparationCase;
        }

        @Override
        public void update(PreparationCase preparationCase) {
            current = preparationCase;
        }

        @Override
        public Optional<PreparationCase> findById(PreparationCaseId id, OrganizationId organizationId) {
            return matches(id, organizationId);
        }

        @Override
        public Optional<PreparationCase> findByIdForUpdate(PreparationCaseId id, OrganizationId organizationId) {
            lockedLookups++;
            return matches(id, organizationId);
        }

        private Optional<PreparationCase> matches(PreparationCaseId id, OrganizationId organizationId) {
            return Optional.ofNullable(current)
                    .filter(value -> value.id().equals(id))
                    .filter(value -> value.organizationId().equals(organizationId));
        }
    }

    private static final class RecordingInvitationRepository implements PatientInvitationRepository {
        private PatientInvitation current;
        private PatientInvitation added;
        private int digestLookups;

        @Override
        public Optional<PatientInvitation> findActiveForUpdate(
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            return Optional.ofNullable(current)
                    .filter(value -> value.preparationCaseId().equals(preparationCaseId))
                    .filter(value -> value.organizationId().equals(organizationId))
                    .filter(value -> value.status() == PatientInvitationStatus.ACTIVE);
        }

        @Override
        public Optional<PatientInvitation> findByIdForUpdate(
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            return Optional.ofNullable(current)
                    .filter(value -> value.id().equals(invitationId))
                    .filter(value -> value.preparationCaseId().equals(preparationCaseId))
                    .filter(value -> value.organizationId().equals(organizationId));
        }

        @Override
        public Optional<PatientInvitation> findByTokenDigestForUpdate(TokenDigest tokenDigest) {
            digestLookups++;
            return Optional.ofNullable(current)
                    .filter(value -> value.tokenDigest().equals(tokenDigest));
        }

        @Override
        public Optional<PatientInvitationScope> findScopeByTokenDigest(TokenDigest tokenDigest) {
            digestLookups++;
            return Optional.ofNullable(current)
                    .filter(value -> value.tokenDigest().equals(tokenDigest))
                    .map(value -> new PatientInvitationScope(
                            value.id(), value.organizationId(), value.preparationCaseId()));
        }

        @Override
        public void add(PatientInvitation invitation) {
            added = invitation;
            current = invitation;
        }

        @Override
        public void update(PatientInvitation invitation) {
            current = invitation;
        }
    }

    private static final class RecordingAccessSessionRepository implements PatientAccessSessionRepository {
        private PatientAccessSession added;
        private final List<PatientInvitationId> revokedInvitationIds = new ArrayList<>();

        @Override
        public void add(PatientAccessSession session) {
            added = session;
        }

        @Override
        public Optional<backend.preparation.application.port.out.PatientAccessSessionScope> findScopeByTokenDigest(
                TokenDigest tokenDigest) {
            return Optional.empty();
        }

        @Override
        public Optional<backend.preparation.application.port.out.PatientAccessSessionScope> findScopeByIdentity(
                UUID id,
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            return Optional.empty();
        }

        @Override
        public Optional<PatientAccessSession> findByIdForUpdate(
                UUID id,
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            return Optional.empty();
        }

        @Override
        public void update(PatientAccessSession session) {
            added = session;
        }

        @Override
        public void revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt) {
            revokedInvitationIds.add(invitationId);
        }
    }
}
