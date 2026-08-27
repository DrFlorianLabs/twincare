package backend.preparation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.transaction.TestTransaction;

import backend.preparation.domain.AppointmentReference;
import backend.preparation.application.port.out.PreparationCaseCreationFingerprint;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import jakarta.persistence.EntityManager;

@DataJpaTest
class JpaPreparationCaseRepositoryAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-21T10:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("cabinet-a");

    @Autowired
    private SpringDataPreparationCaseRepository springDataRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void addOrganization() {
        entityManager.createNativeQuery("""
                INSERT INTO care_organization (id, display_name)
                VALUES ('cabinet-a', 'Synthetic organization')
                """).executeUpdate();
    }

    @Test
    void persistsAndRestoresTheAggregateWithoutNewEvents() {
        JpaPreparationCaseRepositoryAdapter adapter = new JpaPreparationCaseRepositoryAdapter(springDataRepository);
        PreparationCase original = PreparationCase.create(
                new PreparationCaseId(UUID.fromString("84f8648c-dcfe-449e-9845-3ee40a33246d")),
                ORGANIZATION_ID,
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.of(CREATED_AT.plusSeconds(3600)),
                CREATED_AT);
        original.startPatientPreparation(CREATED_AT.plusSeconds(60));
        original.recordPatientReason("Motif patient synthétique", CREATED_AT.plusSeconds(120));

        adapter.add(original);
        entityManager.clear();

        PreparationCase restored = adapter.findById(original.id(), ORGANIZATION_ID).orElseThrow();
        assertThat(restored.status()).isEqualTo(PreparationCaseStatus.COLLECTING);
        assertThat(restored.patientReason()).get()
                .extracting(statement -> statement.text())
                .isEqualTo("Motif patient synthétique");
        assertThat(restored.releaseDomainEvents()).isEmpty();
    }

    @Test
    void scopesLookupByOrganization() {
        JpaPreparationCaseRepositoryAdapter adapter = new JpaPreparationCaseRepositoryAdapter(springDataRepository);
        PreparationCase original = PreparationCase.create(
                new PreparationCaseId(UUID.randomUUID()),
                ORGANIZATION_ID,
                new PatientReference("patient-42"),
                Optional.empty(),
                Optional.of(CREATED_AT.plusSeconds(3600)),
                CREATED_AT);
        adapter.add(original);
        entityManager.clear();

        assertThat(adapter.findById(original.id(), new OrganizationId("cabinet-b"))).isEmpty();
        assertThat(adapter.findById(original.id(), ORGANIZATION_ID)).isPresent();
    }

    @Test
    void persistsAndScopesTheCreationRequestFingerprint() {
        JpaPreparationCaseRepositoryAdapter adapter = new JpaPreparationCaseRepositoryAdapter(springDataRepository);
        UUID requestId = UUID.fromString("adf8f2d0-a6cb-4386-afc2-271fa0a04523");
        PreparationCase original = PreparationCase.create(
                new PreparationCaseId(UUID.fromString("f2ddf130-b3d9-442f-994e-126bd7262f4e")),
                ORGANIZATION_ID,
                new PatientReference("patient-idempotency"),
                Optional.of(new AppointmentReference("appointment-idempotency")),
                Optional.empty(),
                CREATED_AT);

        adapter.add(original, requestId, new PreparationCaseCreationFingerprint("a".repeat(64)));
        entityManager.clear();

        var stored = adapter.findByCreationRequestId(ORGANIZATION_ID, requestId).orElseThrow();
        assertThat(stored.preparationCase().id()).isEqualTo(original.id());
        assertThat(stored.payloadFingerprint().value()).isEqualTo("a".repeat(64));
        assertThat(adapter.findByCreationRequestId(new OrganizationId("cabinet-b"), requestId)).isEmpty();
    }

    @Test
    void everyExplicitCaseUpdateAdvancesTheReviewRevisionEvenAtTheSameTimestamp() {
        JpaPreparationCaseRepositoryAdapter adapter = new JpaPreparationCaseRepositoryAdapter(springDataRepository);
        PreparationCase original = PreparationCase.create(
                new PreparationCaseId(UUID.randomUUID()),
                ORGANIZATION_ID,
                new PatientReference("patient-revision"),
                Optional.of(new AppointmentReference("appointment-revision")),
                Optional.empty(),
                CREATED_AT);
        adapter.add(original);
        entityManager.clear();

        PreparationCase locked = adapter.findByIdForUpdate(original.id(), ORGANIZATION_ID).orElseThrow();
        locked.touchVisibleContent(CREATED_AT);
        adapter.update(locked);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(adapter.findById(original.id(), ORGANIZATION_ID).orElseThrow().revision()).isOne();
    }
}
