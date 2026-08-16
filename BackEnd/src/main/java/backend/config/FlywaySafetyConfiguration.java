package backend.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywaySafetyConfiguration {

    private static final MigrationVersion DESTRUCTIVE_PROTOTYPE_VERSION = MigrationVersion.fromVersion("3");
    private static final Set<String> LEGACY_TABLES_REPLACED_BY_V3 = Set.of(
            "app_utilisateur",
            "dossier_patient",
            "document",
            "interaction_chatbot",
            "question",
            "validation");

    @Bean
    public FlywayMigrationStrategy failClosedFlywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            assertSafeToMigrate(dataSource);
            flyway.migrate();
        };
    }

    void assertSafeToMigrate(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> existingTables = existingTableNames(connection);
            if (!existingTables.contains("flyway_schema_history")) {
                return;
            }

            MigrationVersion currentVersion = currentVersion(connection);
            if (currentVersion != null
                    && currentVersion.compareTo(DESTRUCTIVE_PROTOTYPE_VERSION) >= 0) {
                return;
            }

            boolean legacySchemaExists = LEGACY_TABLES_REPLACED_BY_V3.stream()
                    .anyMatch(existingTables::contains);
            if (legacySchemaExists) {
                throw new IllegalStateException(
                        "Unsafe Flyway state: destructive prototype migration V3 is pending on a legacy schema");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to verify Flyway migration safety", e);
        }
    }

    private Set<String> existingTableNames(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> tableNames = new java.util.HashSet<>();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[] { "TABLE" })) {
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return tableNames;
    }

    private MigrationVersion currentVersion(Connection connection) throws SQLException {
        String sql = """
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE AND version IS NOT NULL
                ORDER BY installed_rank DESC
                LIMIT 1
                """;
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                return null;
            }
            return MigrationVersion.fromVersion(result.getString("version"));
        }
    }
}
