package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.preparation.application.InvalidPatientReasonCommandException;
import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.RecordPatientReasonCommand;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientAccessSessionScope;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PatientInvitationScope;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationCaseCreationFingerprint;
import backend.preparation.application.port.out.StoredPreparationCaseCreation;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.ProvenanceType;
import backend.preparation.domain.TokenDigest;

class PatientReasonServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void recordsOnePatientDeclaredReasonInTheServerOwnedCase() {
        Fixture fixture = new Fixture();

        fixture.service.record(new RecordPatientReasonCommand(
                fixture.grant, "  Fatigue depuis trois semaines  "));

        assertThat(fixture.preparationCase.status()).isEqualTo(PreparationCaseStatus.COLLECTING);
        assertThat(fixture.preparationCase.patientReason()).get().satisfies(reason -> {
            assertThat(reason.text()).isEqualTo("Fatigue depuis trois semaines");
            assertThat(reason.provenance()).isEqualTo(ProvenanceType.PATIENT_DECLARED);
            assertThat(reason.recordedAt()).isEqualTo(NOW);
        });
        assertThat(fixture.cases.updateCount).isOne();
    }

    @Test
    void identicalPutIsIdempotentAndDoesNotRewriteClinicalData() {
        Fixture fixture = new Fixture();
        fixture.service.record(new RecordPatientReasonCommand(fixture.grant, "Motif synthétique"));
        fixture.preparationCase.releaseDomainEvents();

        fixture.service.record(new RecordPatientReasonCommand(fixture.grant, "  Motif synthétique "));

        assertThat(fixture.cases.updateCount).isOne();
        assertThat(fixture.preparationCase.releaseDomainEvents()).isEmpty();
    }

    @Test
    void revokedSessionFailsClosedBeforeAnyClinicalWrite() {
        Fixture fixture = new Fixture();
        fixture.session.revoke(NOW.minusSeconds(1));

        assertThatThrownBy(() -> fixture.service.record(
                new RecordPatientReasonCommand(fixture.grant, "Motif synthétique")))
                .isInstanceOf(PatientSessionUnavailableException.class);
        assertThat(fixture.preparationCase.patientReason()).isEmpty();
        assertThat(fixture.cases.updateCount).isZero();
    }

    @Test
    void usesTheSharedLockOrderAndCapturesTimeOnlyAfterLocks() {
        Fixture fixture = new Fixture();

        fixture.service.record(new RecordPatientReasonCommand(fixture.grant, "Motif synthétique"));

        assertThat(fixture.operations)
                .containsExactly("scope", "case", "invitation", "session", "clock");
    }

    @Test
    void invalidOrOversizedReasonIsRejectedWithoutEchoingContent() {
        Fixture fixture = new Fixture();
        String oversized = "x".repeat(4_001);

        assertThatThrownBy(() -> new RecordPatientReasonCommand(fixture.grant, " "))
                .isInstanceOf(InvalidPatientReasonCommandException.class);
        assertThatThrownBy(() -> new RecordPatientReasonCommand(fixture.grant, oversized))
                .isInstanceOf(InvalidPatientReasonCommandException.class)
                .hasMessageNotContaining(oversized);
        assertThat(new RecordPatientReasonCommand(fixture.grant, "Motif synthétique").toString())
                .doesNotContain("Motif synthétique", fixture.grant.sessionId().toString());
    }

    private static final class Fixture {
        private final OrganizationId organizationId = new OrganizationId("synthetic-clinic");
        private final PreparationCaseId caseId = new PreparationCaseId(
                UUID.fromString("b2fd6687-3a6a-4f1e-b29f-959746390e0b"));
        private final PatientInvitationId invitationId = new PatientInvitationId(
                UUID.fromString("a06a8793-970c-4ae8-912a-aef0816c8abf"));
        private final PreparationCase preparationCase;
        private final PatientInvitation invitation;
        private final PatientAccessSession session;
        private final PatientAccessGrant grant;
        private final InMemoryCaseRepository cases;
        private final RecordPatientReasonService service;
        private final List<String> operations = new ArrayList<>();

        private Fixture() {
            preparationCase = PreparationCase.create(
                    caseId,
                    organizationId,
                    new PatientReference("synthetic-patient"),
                    Optional.of(new AppointmentReference("synthetic-appointment")),
                    Optional.empty(),
                    NOW.minusSeconds(120));
            preparationCase.startPatientPreparation(NOW.minusSeconds(30));
            preparationCase.releaseDomainEvents();
            invitation = PatientInvitation.issue(
                    invitationId,
                    organizationId,
                    caseId,
                    1L,
                    new TokenDigest("test-v1", "a".repeat(64)),
                    NOW.minusSeconds(60),
                    NOW.plusSeconds(3600));
            invitation.consume(NOW.minusSeconds(30));
            session = PatientAccessSession.issue(
                    UUID.fromString("230fd7be-8869-466b-af6d-f96e61d26775"),
                    invitation,
                    new TokenDigest("test-v1", "b".repeat(64)),
                    NOW.minusSeconds(30));
            grant = new PatientAccessGrant(
                    session.id(), invitation.id(), organizationId, caseId, session.expiresAt());
            cases = new InMemoryCaseRepository(preparationCase, operations);
            service = new RecordPatientReasonService(
                    cases,
                    new InMemoryInvitationRepository(invitation, operations),
                    new InMemorySessionRepository(session, operations),
                    event -> { },
                    new RecordingClock(operations));
        }
    }

    private static final class InMemoryCaseRepository implements PreparationCaseRepository {
        private final PreparationCase preparationCase;
        private final List<String> operations;
        private int updateCount;

        private InMemoryCaseRepository(PreparationCase preparationCase, List<String> operations) {
            this.preparationCase = preparationCase;
            this.operations = operations;
        }

        @Override
        public void add(PreparationCase ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(
                PreparationCase ignored,
                UUID requestId,
                PreparationCaseCreationFingerprint fingerprint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long update(PreparationCase candidate) {
            assertThat(candidate).isSameAs(preparationCase);
            updateCount++;
            return candidate.revision() + 1;
        }

        @Override
        public Optional<PreparationCase> findById(PreparationCaseId id, OrganizationId organizationId) {
            return matches(id, organizationId) ? Optional.of(preparationCase) : Optional.empty();
        }

        @Override
        public Optional<PreparationCase> findByIdForUpdate(PreparationCaseId id, OrganizationId organizationId) {
            operations.add("case");
            return findById(id, organizationId);
        }

        @Override
        public Optional<StoredPreparationCaseCreation> findByCreationRequestId(
                OrganizationId organizationId,
                UUID requestId) {
            return Optional.empty();
        }

        private boolean matches(PreparationCaseId id, OrganizationId organizationId) {
            return preparationCase.id().equals(id)
                    && preparationCase.organizationId().equals(organizationId);
        }
    }

    private static final class InMemoryInvitationRepository implements PatientInvitationRepository {
        private final PatientInvitation invitation;
        private final List<String> operations;

        private InMemoryInvitationRepository(PatientInvitation invitation, List<String> operations) {
            this.invitation = invitation;
            this.operations = operations;
        }

        @Override
        public Optional<PatientInvitation> findByIdForUpdate(
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            operations.add("invitation");
            return invitation.id().equals(invitationId)
                    && invitation.preparationCaseId().equals(preparationCaseId)
                    && invitation.organizationId().equals(organizationId)
                    ? Optional.of(invitation)
                    : Optional.empty();
        }

        @Override
        public Optional<PatientInvitation> findActiveForUpdate(
                PreparationCaseId preparationCaseId, OrganizationId organizationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PatientInvitation> findByTokenDigestForUpdate(TokenDigest tokenDigest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PatientInvitation> findAllByCaseForUpdate(
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PatientInvitationScope> findScopeByTokenDigest(TokenDigest tokenDigest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(PatientInvitation ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(PatientInvitation ignored) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemorySessionRepository implements PatientAccessSessionRepository {
        private final PatientAccessSession session;
        private final List<String> operations;

        private InMemorySessionRepository(PatientAccessSession session, List<String> operations) {
            this.session = session;
            this.operations = operations;
        }

        @Override
        public Optional<PatientAccessSession> findByIdForUpdate(
                UUID id,
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            operations.add("session");
            return session.id().equals(id)
                    && session.invitationId().equals(invitationId)
                    && session.preparationCaseId().equals(preparationCaseId)
                    && session.organizationId().equals(organizationId)
                    ? Optional.of(session)
                    : Optional.empty();
        }

        @Override
        public Optional<PatientAccessSessionScope> findScopeByTokenDigest(TokenDigest tokenDigest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PatientAccessSessionScope> findScopeByIdentity(
                UUID id,
                PatientInvitationId invitationId,
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            operations.add("scope");
            if (!session.id().equals(id)
                    || !session.invitationId().equals(invitationId)
                    || !session.preparationCaseId().equals(preparationCaseId)
                    || !session.organizationId().equals(organizationId)) {
                return Optional.empty();
            }
            return Optional.of(new PatientAccessSessionScope(
                    session.id(),
                    session.invitationId(),
                    session.organizationId(),
                    session.preparationCaseId(),
                    PatientAccessSessionScope.CONTRIBUTION_SCOPE,
                    backend.preparation.domain.PatientInvitationStatus.CONSUMED,
                    PreparationCaseStatus.PATIENT_STARTED,
                    session.issuedAt(),
                    session.expiresAt(),
                    session.revokedAt()));
        }

        @Override
        public List<PatientAccessSession> findAllByCaseForUpdate(
                PreparationCaseId preparationCaseId,
                OrganizationId organizationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(PatientAccessSession ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(PatientAccessSession ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingClock extends Clock {
        private final List<String> operations;

        private RecordingClock(List<String> operations) {
            this.operations = operations;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(NOW, zone);
        }

        @Override
        public Instant instant() {
            operations.add("clock");
            return NOW;
        }
    }
}
