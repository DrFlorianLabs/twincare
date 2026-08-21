package backend.preparation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import backend.preparation.adapter.config.TransactionalProfessionalPreparationQueries;
import backend.preparation.application.port.in.GetProfessionalPreparationViewCommand;

@SpringBootTest
class ProfessionalPreparationRepeatableReadIntegrationTest {

    private static final String ORGANIZATION_ID = "repeatable-read-org";
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired
    private SpringDataProfessionalPreparationQueryRepository repository;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM preparation_task");
        jdbcTemplate.update("DELETE FROM preparation_case");
    }

    @Test
    void detailedQueryTransactionUsesRepeatableRead() throws Exception {
        Method method = TransactionalProfessionalPreparationQueries.class.getMethod(
                "get", GetProfessionalPreparationViewCommand.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
        assertThat(annotation.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void taskInsertedBetweenHeaderAndTaskQueriesIsNotMixedIntoTheSnapshot() throws Exception {
        UUID caseId = UUID.randomUUID();
        insertCase(caseId);
        CountDownLatch headerRead = new CountDownLatch(1);
        CountDownLatch taskCommitted = new CountDownLatch(1);
        var template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var observedTaskCount = executor.submit(() -> template.execute(status -> {
                assertThat(repository.findView(ORGANIZATION_ID, caseId)).isPresent();
                headerRead.countDown();
                try {
                    taskCommitted.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted repeatable-read test", exception);
                }
                return repository.findTasks(
                        ORGANIZATION_ID,
                        caseId,
                        PageRequest.of(0, 100)).size();
            }));

            headerRead.await();
            insertTask(caseId);
            taskCommitted.countDown();

            assertThat(observedTaskCount.get()).isZero();
        }
    }

    private void insertCase(UUID caseId) {
        jdbcTemplate.update(
                """
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference,
                    status, patient_reason, patient_reason_provenance,
                    patient_reason_recorded_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                caseId,
                ORGANIZATION_ID,
                "synthetic-patient",
                "synthetic-appointment",
                "COLLECTING",
                "Synthetic exact reason",
                "PATIENT_DECLARED",
                NOW,
                NOW,
                NOW,
                0L);
    }

    private void insertTask(UUID caseId) {
        jdbcTemplate.update(
                """
                INSERT INTO preparation_task (
                    id, organization_id, preparation_case_id, creation_request_id,
                    description, assignee_user_id, status, created_by_user_id,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                ORGANIZATION_ID,
                caseId,
                UUID.randomUUID(),
                "Synthetic task",
                42L,
                "OPEN",
                42L,
                NOW,
                NOW,
                0L);
    }
}
