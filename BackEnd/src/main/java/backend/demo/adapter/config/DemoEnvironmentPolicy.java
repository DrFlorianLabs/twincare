package backend.demo.adapter.config;

import java.util.Arrays;

import org.springframework.core.env.Environment;

final class DemoEnvironmentPolicy {

    private static final String LOCAL_DEMO_DATABASE =
            "jdbc:h2:file:./.twincare-demo/twincare;MODE=PostgreSQL;"
                    + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    private static final String TEST_DEMO_DATABASE =
            "jdbc:h2:mem:twincare_demo_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private DemoEnvironmentPolicy() {
    }

    static void requireSafe(Environment environment) {
        boolean demoProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("demo");
        String serverAddress = environment.getProperty("server.address", "");
        String datasourceUrl = environment.getProperty("spring.datasource.url", "");
        boolean isolatedDatasource = LOCAL_DEMO_DATABASE.equals(datasourceUrl)
                || TEST_DEMO_DATABASE.equals(datasourceUrl);

        if (!demoProfileActive || !"127.0.0.1".equals(serverAddress) || !isolatedDatasource) {
            throw new IllegalStateException(
                    "Demo mode requires the demo profile, loopback binding and isolated H2 storage");
        }
    }
}
