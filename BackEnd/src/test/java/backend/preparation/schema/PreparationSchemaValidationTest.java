package backend.preparation.schema;

import static org.assertj.core.api.Assertions.assertThat;

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
