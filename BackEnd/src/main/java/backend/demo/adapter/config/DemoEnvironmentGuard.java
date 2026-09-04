package backend.demo.adapter.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("demo")
public final class DemoEnvironmentGuard {

    private final Environment environment;

    public DemoEnvironmentGuard(Environment environment) {
        this.environment = environment;
    }

    public void requireSafeDemoEnvironment() {
        DemoEnvironmentPolicy.requireSafe(environment);
    }
}
