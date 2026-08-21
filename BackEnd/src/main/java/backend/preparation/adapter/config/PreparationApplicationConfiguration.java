package backend.preparation.adapter.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationCaseIdGenerator;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.service.CreatePreparationCaseService;

@Configuration
public class PreparationApplicationConfiguration {

    @Bean
    Clock preparationClock() {
        return Clock.systemUTC();
    }

    @Bean
    CreatePreparationCaseUseCase createPreparationCaseUseCase(
            PreparationCaseAuthorization authorization,
            PreparationCaseRepository repository,
            PreparationCaseIdGenerator idGenerator,
            Clock preparationClock) {
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                authorization,
                repository,
                idGenerator,
                preparationClock);
        return new TransactionalCreatePreparationCaseUseCase(service);
    }
}
