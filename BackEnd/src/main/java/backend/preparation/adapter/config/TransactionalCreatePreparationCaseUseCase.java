package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.in.CreatePreparationCaseResult;
import backend.preparation.application.port.in.CreatePreparationCaseUseCase;

public class TransactionalCreatePreparationCaseUseCase implements CreatePreparationCaseUseCase {

    private final CreatePreparationCaseUseCase delegate;

    public TransactionalCreatePreparationCaseUseCase(CreatePreparationCaseUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public CreatePreparationCaseResult create(CreatePreparationCaseCommand command) {
        return delegate.create(command);
    }
}
