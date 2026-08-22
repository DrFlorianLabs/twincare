package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.ClosePreparationCaseCommand;
import backend.preparation.application.port.in.ClosePreparationCaseResult;
import backend.preparation.application.port.in.ClosePreparationCaseUseCase;

public class TransactionalClosePreparationCaseUseCase implements ClosePreparationCaseUseCase {

    private final ClosePreparationCaseUseCase delegate;

    public TransactionalClosePreparationCaseUseCase(ClosePreparationCaseUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public ClosePreparationCaseResult close(ClosePreparationCaseCommand command) {
        return delegate.close(command);
    }
}
