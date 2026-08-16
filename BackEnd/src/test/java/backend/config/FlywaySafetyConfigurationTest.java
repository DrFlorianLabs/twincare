package backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class FlywaySafetyConfigurationTest {

    @Test
    void blocksPendingDestructiveMigrationOnLegacySchema() throws Exception {
        JdbcDataSource dataSource = dataSource();
        createHistory(dataSource, "2");
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE app_utilisateur (id BIGINT PRIMARY KEY)");
        }

        assertThatThrownBy(() -> new FlywaySafetyConfiguration().assertSafeToMigrate(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("migration V3");
    }

    @Test
    void allowsSchemaAlreadyBaselinedAfterDestructiveMigration() throws Exception {
        JdbcDataSource dataSource = dataSource();
        createHistory(dataSource, "4");
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE app_utilisateur (id BIGINT PRIMARY KEY)");
        }

        assertThatCode(() -> new FlywaySafetyConfiguration().assertSafeToMigrate(dataSource))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsEmptyDatabaseToRunInitialMigrations() {
        JdbcDataSource dataSource = dataSource();

        assertThatCode(() -> new FlywaySafetyConfiguration().assertSafeToMigrate(dataSource))
                .doesNotThrowAnyException();
    }

    private JdbcDataSource dataSource() {
        String databaseName = "flyway_safety_" + UUID.randomUUID().toString().replace("-", "");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void createHistory(JdbcDataSource dataSource, String version) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE flyway_schema_history (
                        installed_rank INTEGER NOT NULL,
                        version VARCHAR(50),
                        success BOOLEAN NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO flyway_schema_history VALUES (1, '" + version + "', TRUE)");
        }
    }
}
