package backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class FlywayBaselineSafetyTest {

    @Test
    void upgradesAnExistingV6ReasonWithoutLosingClinicalData() throws Exception {
        String databaseName = "v6_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .target(MigrationVersion.fromVersion("6"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO care_organization (id, display_name, created_at)
                    VALUES ('synthetic-v6-org', 'Synthetic V6 organization',
                            TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00')
                    """);
            statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, appointment_reference,
                        status, patient_reason, patient_reason_provenance,
                        patient_reason_recorded_at, created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'synthetic-v6-org', 'synthetic-v6-patient', 'synthetic-v6-appointment',
                        'COLLECTING', 'Motif synthétique préexistant', 'PATIENT_DECLARED',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:01:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:01:00+00', 1
                    )
                    """);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT patient_reason
                        FROM preparation_case
                        WHERE organization_id = 'synthetic-v6-org'
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("Motif synthétique préexistant");
        }
    }

    @Test
    void upgradesV7WithTenantBoundSingletonAttachmentConstraints() throws Exception {
        String databaseName = "v7_attachment_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .target(MigrationVersion.fromVersion("7"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO care_organization (id, display_name, created_at)
                    VALUES
                        ('attachment-org-a', 'Synthetic A', CURRENT_TIMESTAMP),
                        ('attachment-org-b', 'Synthetic B', CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, appointment_reference,
                        status, created_at, updated_at, version
                    ) VALUES (
                        UUID '10000000-0000-0000-0000-000000000001',
                        'attachment-org-a', 'synthetic-patient', 'synthetic-appointment',
                        'PATIENT_STARTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    ), (
                        UUID '10000000-0000-0000-0000-000000000002',
                        'attachment-org-a', 'synthetic-patient-2', 'synthetic-appointment-2',
                        'PATIENT_STARTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO preparation_attachment (
                        id, organization_id, preparation_case_id, storage_key,
                        display_filename, media_type, size_bytes, sha256_digest,
                        provenance, uploaded_at, updated_at, version
                    ) VALUES (
                        UUID '20000000-0000-0000-0000-000000000001',
                        'attachment-org-a', UUID '10000000-0000-0000-0000-000000000001',
                        UUID '30000000-0000-0000-0000-000000000001',
                        'synthetic.pdf', 'PDF', 42, REPEAT('a', 64),
                        'PATIENT_UPLOADED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_attachment (
                        id, organization_id, preparation_case_id, storage_key,
                        display_filename, media_type, size_bytes, sha256_digest,
                        provenance, uploaded_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'attachment-org-a',
                        UUID '10000000-0000-0000-0000-000000000001', RANDOM_UUID(),
                        'duplicate.pdf', 'PDF', 42, REPEAT('b', 64),
                        'PATIENT_UPLOADED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """))
                    .hasMessageContaining("UQ_PREPARATION_ATTACHMENT_CASE");

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_attachment (
                        id, organization_id, preparation_case_id, storage_key,
                        display_filename, media_type, size_bytes, sha256_digest,
                        provenance, uploaded_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'attachment-org-b',
                        UUID '10000000-0000-0000-0000-000000000001', RANDOM_UUID(),
                        'cross-tenant.pdf', 'PDF', 42, REPEAT('c', 64),
                        'PATIENT_UPLOADED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """))
                    .hasMessageContaining("FK_PREPARATION_ATTACHMENT_CASE_ORGANIZATION");

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_attachment (
                        id, organization_id, preparation_case_id, storage_key,
                        display_filename, media_type, size_bytes, sha256_digest,
                        provenance, uploaded_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'attachment-org-a',
                        UUID '10000000-0000-0000-0000-000000000002', RANDOM_UUID(),
                        'invalid-digest.pdf', 'PDF', 42, REPEAT('g', 64),
                        'PATIENT_UPLOADED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """))
                    .hasMessageContaining("CK_PREPARATION_ATTACHMENT_DIGEST");
        }
    }

    @Test
    void upgradesV8WithTenantBoundTaskAndConfirmationConstraints() throws Exception {
        String databaseName = "v8_tasks_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .target(MigrationVersion.fromVersion("8"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO care_organization (id, display_name, created_at)
                    VALUES
                        ('task-org-a', 'Synthetic A', CURRENT_TIMESTAMP),
                        ('task-org-b', 'Synthetic B', CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO app_utilisateur (
                        id, nom, prenom, date_naissance, email, mot_de_passe, role, sexe
                    ) VALUES
                        (9101, 'Synthetic', 'Clinician', DATE '1990-01-01',
                            'clinician-a@example.test', 'not-a-real-password', 'MEDECIN', 'NON_PRECISE'),
                        (9102, 'Synthetic', 'Assistant', DATE '1990-01-01',
                            'assistant-b@example.test', 'not-a-real-password', 'ASSISTANT_MEDICAL', 'NON_PRECISE')
                    """);
            statement.execute("""
                    INSERT INTO organization_membership (
                        id, organization_id, user_id, membership_role, active, created_at
                    ) VALUES
                        (RANDOM_UUID(), 'task-org-a', 9101, 'CLINICIAN', TRUE, CURRENT_TIMESTAMP),
                        (RANDOM_UUID(), 'task-org-b', 9102, 'MEDICAL_ASSISTANT', TRUE, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, appointment_reference,
                        status, patient_reason, patient_reason_provenance,
                        patient_reason_recorded_at, created_at, updated_at, version
                    ) VALUES (
                        UUID '70000000-0000-0000-0000-000000000001',
                        'task-org-a', 'synthetic-patient', 'synthetic-appointment',
                        'READY_FOR_REVIEW', 'Motif synthétique', 'PATIENT_DECLARED',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:01:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:01:00+00', 1
                    )
                    """);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO preparation_task (
                        id, organization_id, preparation_case_id, creation_request_id,
                        description, assignee_user_id, status, created_by_user_id,
                        created_at, updated_at, version
                    ) VALUES (
                        UUID '71000000-0000-0000-0000-000000000001', 'task-org-a',
                        UUID '70000000-0000-0000-0000-000000000001',
                        UUID '72000000-0000-0000-0000-000000000001',
                        'Tâche synthétique', 9101, 'OPEN', 9101,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_task (
                        id, organization_id, preparation_case_id, creation_request_id,
                        description, assignee_user_id, status, created_by_user_id,
                        created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'task-org-a',
                        UUID '70000000-0000-0000-0000-000000000001',
                        UUID '72000000-0000-0000-0000-000000000001',
                        'Rejeu conflictuel', 9101, 'OPEN', 9101,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """))
                    .hasMessageContaining("UQ_PREPARATION_TASK_CREATION_REQUEST");

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_task (
                        id, organization_id, preparation_case_id, creation_request_id,
                        description, assignee_user_id, status, created_by_user_id,
                        created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'task-org-b',
                        UUID '70000000-0000-0000-0000-000000000001', RANDOM_UUID(),
                        'Tâche inter-organisation interdite', 9102, 'OPEN', 9102,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """))
                    .hasMessageContaining("FK_PREPARATION_TASK_CASE_ORGANIZATION");

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_ready_confirmation (
                        preparation_case_id, organization_id, confirmed_by_user_id,
                        confirmed_at, rule_set_id, rule_set_version,
                        reviewed_case_revision, reviewed_reason_recorded_at, open_task_count
                    ) VALUES (
                        UUID '70000000-0000-0000-0000-000000000001', 'task-org-a', 9101,
                        CURRENT_TIMESTAMP, 'preparation-readiness', 1, 1,
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:01:00+00', 1
                    )
                    """))
                    .hasMessageContaining("CK_READY_CONFIRMATION_REVIEW_EVIDENCE");
        }
    }

    @Test
    void migratesFreshSchemaThroughPrototypeBaseline() throws Exception {
        String databaseName = "fresh_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM question")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(50);
        }

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM preparation_case")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM patient_invitation")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM preparation_attachment")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    @Test
    void baselinesLegacySchemaAfterDestructivePrototypeMigrations() throws Exception {
        String databaseName = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE app_utilisateur (id BIGINT PRIMARY KEY, marker VARCHAR(30))");
            statement.execute("INSERT INTO app_utilisateur (id, marker) VALUES (1, 'synthetic-sentinel')");
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT marker FROM app_utilisateur WHERE id = 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("synthetic-sentinel");
        }


        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO care_organization (id, display_name, created_at)
                    VALUES ('synthetic-org', 'Synthetic organization', CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, status, created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'synthetic-org', 'synthetic-patient', 'CREATED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """))
                    .hasMessageContaining("CK_PREPARATION_CASE_CONSULTATION");

            statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, scheduled_at,
                        status, created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'synthetic-org', 'synthetic-patient',
                        TIMESTAMP WITH TIME ZONE '2030-01-01 10:00:00+00',
                        'CREATED', TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00', 0
                    )
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, scheduled_at,
                        status, created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'synthetic-org', 'synthetic-patient',
                        TIMESTAMP WITH TIME ZONE '2030-01-01 10:00:00+00',
                        'CREATED', TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00', 0
                    )
                    """))
                    .hasMessageContaining("UQ_PREPARATION_CASE_PATIENT_SCHEDULE");

            assertThat(statement.executeQuery("SELECT COUNT(*) FROM patient_invitation").next()).isTrue();

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, appointment_reference,
                        status, patient_reason, patient_reason_provenance,
                        patient_reason_recorded_at, created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'synthetic-org', 'synthetic-patient-blank', 'synthetic-blank',
                        'COLLECTING', '   ', 'PATIENT_DECLARED',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00', 0
                    )
                    """))
                    .hasMessageContaining("CK_PREPARATION_CASE_REASON_NONBLANK");

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO preparation_case (
                        id, organization_id, patient_reference, appointment_reference,
                        status, patient_reason, patient_reason_provenance,
                        patient_reason_recorded_at, created_at, updated_at, version
                    ) VALUES (
                        RANDOM_UUID(), 'synthetic-org', 'synthetic-patient-time', 'synthetic-time',
                        'COLLECTING', 'Motif synthétique', 'PATIENT_DECLARED',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 09:59:59+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-08-21 10:00:00+00', 0
                    )
                    """))
                    .hasMessageContaining("CK_PREPARATION_CASE_REASON_TIMELINE");
        }
    }
}
