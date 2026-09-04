package backend.demo.adapter.config;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Rejects an unsafe demo configuration before datasource and Flyway initialization. */
public final class DemoEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("demo")) {
            DemoEnvironmentPolicy.requireSafe(environment);
        }
    }

    @Override
    public int getOrder() {
        // Config data is loaded at HIGHEST_PRECEDENCE + 10 by Spring Boot.
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }
}
