package backend.preparation.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        classes = PreparationSchemaValidationTest.ValidationConfiguration.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:preparation_schema_validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=true",
                "spring.flyway.baseline-version=4",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false"
        })
class PreparationSchemaValidationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywaySchemaMatchesThePreparationJpaMappings() {
        String version = jdbcTemplate.queryForObject(
                """
                SELECT "version"
                FROM "flyway_schema_history"
                WHERE "success" = TRUE AND "version" IS NOT NULL
                ORDER BY "installed_rank" DESC
                LIMIT 1
                """,
                String.class);

        assertThat(version).isEqualTo("14");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM patient_invitation", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM patient_access_session", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preparation_attachment", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preparation_task", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_ready_confirmation", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_case_closure", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clinical_fact", Integer.class))
                .isZero();
    }


    @Test
    @Transactional
    void clinicalAuditConstraintsRejectMissingSubjectAndWrongActorEvidence() {
        Instant now = Instant.parse("2026-09-01T08:00:00Z");
        UUID caseId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_utilisateur (
                    id, nom, prenom, date_naissance, email, mot_de_passe, role, sexe
                ) VALUES (7001, 'Synthetic', 'Audit', DATE '1990-01-01',
                    'schema-audit@synthetic.invalid', 'synthetic-unusable-password', 'MEDECIN', 'NON_PRECISE')
                """);
        jdbcTemplate.update("""
                INSERT INTO care_organization (id, display_name, created_at)
                VALUES ('schema-clinical-audit', 'Synthetic audit schema organization', ?)
                """, now);
        jdbcTemplate.update("""
                INSERT INTO organization_membership (
                    id, organization_id, user_id, membership_role, active, created_at
                ) VALUES (?, 'schema-clinical-audit', 7001, 'CLINICIAN', TRUE, ?)
                """, UUID.randomUUID(), now);
        jdbcTemplate.update("""
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference,
                    status, created_at, updated_at, version
                ) VALUES (?, 'schema-clinical-audit', 'synthetic-patient', 'synthetic-appointment',
                    'CREATED', ?, ?, 0)
                """, caseId, now, now);

        String[] types = { "CLINICAL_FACT_CONFIRMED", "CLINICAL_FACT_REJECTED" };
        for (int index = 0; index < types.length; index++) {
            String type = types[index];
            int sequence = index * 10 + 1;
            insertClinicalAudit(caseId, sequence, type, "STAFF", 7001L, "CLINICAL_FACT", factId);
            assertThatThrownBy(() -> insertClinicalAudit(
                    caseId, sequence + 1, type, "STAFF", 7001L, null, factId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertClinicalAudit(
                    caseId, sequence + 2, type, "STAFF", 7001L, "CLINICAL_FACT", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertClinicalAudit(
                    caseId, sequence + 3, type, "STAFF", 7001L, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertClinicalAudit(
                    caseId, sequence + 4, type, "PATIENT", null, "CLINICAL_FACT", factId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertClinicalAudit(
                    caseId, sequence + 5, type, "STAFF", null, "CLINICAL_FACT", factId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertClinicalAudit(
                    caseId, sequence + 6, type, "STAFF", 7001L, "PREPARATION_TASK", factId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class, caseId)).isEqualTo(2);
    }

    private void insertClinicalAudit(
            UUID caseId, int sequence, String eventType, String actorKind,
            Long actorUserId, String subjectType, UUID subjectId) {
        jdbcTemplate.update("""
                INSERT INTO preparation_audit_event (
                    event_id, organization_id, preparation_case_id, event_sequence, event_type,
                    event_version, actor_kind, actor_user_id, occurred_at, case_revision,
                    subject_type, subject_id
                ) VALUES (?, 'schema-clinical-audit', ?, ?, ?, 1, ?, ?, ?, 1, ?, ?)
                """, UUID.randomUUID(), caseId, sequence, eventType, actorKind, actorUserId,
                Instant.parse("2026-09-01T08:00:00Z"), subjectType, subjectId);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @EntityScan(basePackages = {
            "backend.clinicalcontext.adapter.out.persistence",
            "backend.preparation.adapter.out.persistence",
            "backend.preparation.adapter.out.security"
    })
    @EnableJpaRepositories(basePackages = {
            "backend.clinicalcontext.adapter.out.persistence",
            "backend.preparation.adapter.out.persistence",
            "backend.preparation.adapter.out.security"
    })
    static class ValidationConfiguration {
    }
}
