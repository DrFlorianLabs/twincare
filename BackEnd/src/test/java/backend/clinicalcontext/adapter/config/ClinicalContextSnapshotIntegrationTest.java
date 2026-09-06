package backend.clinicalcontext.adapter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.clinicalcontext.application.port.in.ClinicalContextView;
import backend.clinicalcontext.application.port.in.GetClinicalContextQuery;
import backend.clinicalcontext.application.port.in.GetClinicalContextUseCase;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactCommand;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactUseCase;
import backend.clinicalcontext.application.port.out.AuthorizedClinicalReviewer;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.application.service.GetClinicalContextService;
import backend.clinicalcontext.application.service.ReviewClinicalFactService;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ClinicalReviewerReference;
import backend.clinicalcontext.domain.FactReviewDecision;
import backend.clinicalcontext.domain.FactReviewStatus;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:clinical_context_snapshot;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.hikari.maximum-pool-size=4"
})
class ClinicalContextSnapshotIntegrationTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("snapshot-synthetic-org");
    private static final Instant RECORDED_AT = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-09-01T08:05:00Z");
    private static final long WAIT_SECONDS = 10;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private PreparationCaseRepository preparationCases;
    @Autowired
    private ClinicalFactRepository facts;
    @Autowired
    private PreparationAttachmentRepository attachments;

    private final PreparationCaseId caseId = new PreparationCaseId(UUID.randomUUID());
    private final ClinicalFactId factId = new ClinicalFactId(UUID.randomUUID());

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM clinical_fact WHERE preparation_case_id = ?", caseId.value());
        jdbcTemplate.update("DELETE FROM preparation_case WHERE id = ?", caseId.value());
    }

    @Test
    @Timeout(30)
    void concurrentReviewCannotMixNewFactsWithAnEarlierPreparationRevision() throws Exception {
        insertSyntheticCaseAndFact();
        var headerRead = new CountDownLatch(1);
        var resumeReader = new CountDownLatch(1);
        var writerAttemptedLock = new CountDownLatch(1);
        var writerAcquiredLock = new CountDownLatch(1);

        // These local decorators retain the real database operations. The reader
        // pauses after its header lookup, immediately before the fact query.
        var contextId = new ClinicalContextId(caseId.value());
        var ownerId = new ClinicalContextOwner(ORGANIZATION_ID.value());
        var readerFacts = mock(ClinicalFactRepository.class, delegatesTo(facts));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            // H2 does not enforce PostgreSQL's prohibition on SELECT FOR UPDATE
            // in a read-only transaction, so check the effective boundary here.
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            headerRead.countDown();
            await(resumeReader);
            return facts.findAllByContext(contextId, ownerId);
        }).when(readerFacts).findAllByContext(contextId, ownerId);

        var writerCases = mock(PreparationCaseRepository.class, delegatesTo(preparationCases));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            writerAttemptedLock.countDown();
            var preparationCase = preparationCases.findByIdForUpdate(caseId, ORGANIZATION_ID);
            writerAcquiredLock.countDown();
            return preparationCase;
        }).when(writerCases).findByIdForUpdate(caseId, ORGANIZATION_ID);

        var clock = Clock.fixed(REVIEWED_AT, ZoneOffset.UTC);
        // Authorization and audit are isolated only in these test-owned service
        // instances; the application security beans and HTTP chain stay intact.
        var getService = new GetClinicalContextService(
                owner -> { }, preparationCases, readerFacts, attachments, clock);
        var reviewService = new ReviewClinicalFactService(
                owner -> new AuthorizedClinicalReviewer(
                        new ClinicalReviewerReference("synthetic-reviewer"), 42L),
                writerCases, facts, event -> { }, clock);
        Object proxy = transactionalProxy(getService, reviewService);
        var reader = (GetClinicalContextUseCase) proxy;
        var writer = (ReviewClinicalFactUseCase) proxy;
        var query = new GetClinicalContextQuery(ORGANIZATION_ID, caseId);
        var command = new ReviewClinicalFactCommand(
                ORGANIZATION_ID, caseId, factId, FactReviewDecision.CONFIRM);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var snapshot = executor.submit(() -> reader.get(query));
            await(headerRead);
            var review = executor.submit(() -> writer.review(command));
            await(writerAttemptedLock);

            // The writer has entered its transaction and attempted the parent
            // lock. It must remain blocked while the reader assembles its view.
            assertThrows(TimeoutException.class, () -> review.get(250, TimeUnit.MILLISECONDS));
            assertThat(writerAcquiredLock.getCount()).isEqualTo(1L);

            resumeReader.countDown();
            ClinicalContextView beforeReview = snapshot.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(beforeReview.preparationRevision()).isZero();
            assertThat(beforeReview.facts()).singleElement().satisfies(fact -> {
                assertThat(fact.id()).isEqualTo(factId);
                assertThat(fact.reviewStatus()).isEqualTo(FactReviewStatus.UNREVIEWED);
                assertThat(fact.reviewedAt()).isEmpty();
            });

            assertThat(review.get(WAIT_SECONDS, TimeUnit.SECONDS).status())
                    .isEqualTo(FactReviewStatus.CONFIRMED);
            assertThat(writerAcquiredLock.getCount()).isZero();

            ClinicalContextView afterReview = reader.get(query);
            assertThat(afterReview.preparationRevision()).isEqualTo(1L);
            assertThat(afterReview.facts()).singleElement().satisfies(fact -> {
                assertThat(fact.id()).isEqualTo(factId);
                assertThat(fact.reviewStatus()).isEqualTo(FactReviewStatus.CONFIRMED);
                assertThat(fact.reviewedAt()).contains(REVIEWED_AT);
            });
        } finally {
            resumeReader.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("snapshot workers finish before database cleanup").isTrue();
        }
    }

    private Object transactionalProxy(
            GetClinicalContextUseCase getService,
            ReviewClinicalFactUseCase reviewService) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(new TransactionalClinicalContextUseCases(getService, reviewService));
        factory.setInterfaces(GetClinicalContextUseCase.class, ReviewClinicalFactUseCase.class);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("concurrent transaction rendezvous").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted snapshot integration test", exception);
        }
    }

    private void insertSyntheticCaseAndFact() {
        jdbcTemplate.update(
                """
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference,
                    status, patient_reason, patient_reason_provenance,
                    patient_reason_recorded_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'COLLECTING', ?, 'PATIENT_DECLARED', ?, ?, ?, 0)
                """,
                caseId.value(), ORGANIZATION_ID.value(),
                "synthetic-patient", "synthetic-appointment", "Synthetic consultation reason",
                RECORDED_AT, RECORDED_AT, RECORDED_AT);
        jdbcTemplate.update(
                """
                INSERT INTO clinical_fact (
                    id, organization_id, preparation_case_id, fact_kind, text_value,
                    provenance, certainty, recorded_at, lifecycle_status, current_slot,
                    review_status, version
                ) VALUES (?, ?, ?, 'CONSULTATION_REASON', ?, 'PATIENT_DECLARED',
                          'NOT_ASSESSED', ?, 'CURRENT', 1, 'UNREVIEWED', 0)
                """,
                factId.value(), ORGANIZATION_ID.value(), caseId.value(),
                "Synthetic consultation reason", RECORDED_AT);
    }
}
