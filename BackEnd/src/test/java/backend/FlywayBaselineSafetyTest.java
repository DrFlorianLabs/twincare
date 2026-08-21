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
        }
    }
}
