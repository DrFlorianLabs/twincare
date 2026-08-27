package backend.preparation.adapter.config;

import org.springframework.transaction.support.TransactionTemplate;

import backend.preparation.application.PreparationCaseConflictException;
import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.in.CreatePreparationCaseResult;
import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.application.service.CreatePreparationCaseService;

public class TransactionalCreatePreparationCaseUseCase implements CreatePreparationCaseUseCase {

    private final CreatePreparationCaseService delegate;
    private final TransactionTemplate transactions;

    public TransactionalCreatePreparationCaseUseCase(
            CreatePreparationCaseService delegate,
            TransactionTemplate transactions) {
        this.delegate = delegate;
        this.transactions = transactions;
    }

    @Override
    public CreatePreparationCaseResult create(CreatePreparationCaseCommand command) {
        try {
            return transactions.execute(status -> delegate.create(command));
        } catch (PreparationCaseConflictException conflict) {
            return transactions.execute(status -> delegate.replayAfterConflict(command));
        }
    }
}
