package backend.demo.adapter.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DemoEnvironmentGuardTest {

    @Test
    void acceptsOnlyTheDedicatedLocalDatabaseOnLoopback() {
        DemoEnvironmentGuard guard = new DemoEnvironmentGuard(demoEnvironment(
                "127.0.0.1",
                "jdbc:h2:file:./.twincare-demo/twincare;MODE=PostgreSQL;"
                        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"));

        assertThatCode(guard::requireSafeDemoEnvironment).doesNotThrowAnyException();
    }

    @Test
    void rejectsAProductionDatabaseEvenWithTheDemoProfile() {
        DemoEnvironmentGuard guard = new DemoEnvironmentGuard(demoEnvironment(
                "127.0.0.1",
                "jdbc:postgresql://localhost/twincare"));

        assertThatThrownBy(guard::requireSafeDemoEnvironment)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnotherH2FileAndAnyNonLoopbackBinding() {
        DemoEnvironmentGuard unrelatedH2 = new DemoEnvironmentGuard(demoEnvironment(
                "127.0.0.1",
                "jdbc:h2:file:./another-database;MODE=PostgreSQL"));
        DemoEnvironmentGuard networkExposed = new DemoEnvironmentGuard(demoEnvironment(
                "0.0.0.0",
                "jdbc:h2:file:./.twincare-demo/twincare;MODE=PostgreSQL;"
                        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"));

        assertThatThrownBy(unrelatedH2::requireSafeDemoEnvironment)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(networkExposed::requireSafeDemoEnvironment)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAdditionalDatabaseInitializationOptions() {
        DemoEnvironmentGuard guard = new DemoEnvironmentGuard(demoEnvironment(
                "127.0.0.1",
                "jdbc:h2:mem:twincare_demo_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=SELECT 1"));

        assertThatThrownBy(guard::requireSafeDemoEnvironment)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupPostProcessorRejectsUnsafeDemoBeforeTheApplicationContext() {
        MockEnvironment environment = demoEnvironment(
                "127.0.0.1",
                "jdbc:postgresql://localhost/twincare");

        assertThatThrownBy(() -> new DemoEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class);
    }

    private MockEnvironment demoEnvironment(String serverAddress, String datasourceUrl) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        environment.setProperty("server.address", serverAddress);
        environment.setProperty("spring.datasource.url", datasourceUrl);
        return environment;
    }
}
