package backend.demo.adapter.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import backend.demo.application.DemoScenarioService;

@Component
@Profile("demo")
public final class DemoDataInitializer implements ApplicationRunner {

    private final DemoScenarioService scenario;

    public DemoDataInitializer(DemoScenarioService scenario) {
        this.scenario = scenario;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        scenario.reset();
    }
}
