package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.ChangePreparationTaskStatusCommand;
import backend.preparation.application.port.in.ChangePreparationTaskStatusUseCase;
import backend.preparation.application.port.in.ConfirmPreparationReadyCommand;
import backend.preparation.application.port.in.ConfirmPreparationReadyUseCase;
import backend.preparation.application.port.in.CreatePreparationTaskCommand;
import backend.preparation.application.port.in.CreatePreparationTaskResult;
import backend.preparation.application.port.in.CreatePreparationTaskUseCase;
import backend.preparation.application.port.in.PreparationReadyConfirmationResult;
import backend.preparation.application.port.in.PreparationTaskResult;

public class TransactionalProfessionalPreparationCommands implements
        CreatePreparationTaskUseCase,
        ChangePreparationTaskStatusUseCase,
        ConfirmPreparationReadyUseCase {

    private final CreatePreparationTaskUseCase createDelegate;
    private final ChangePreparationTaskStatusUseCase statusDelegate;
    private final ConfirmPreparationReadyUseCase confirmationDelegate;

    public TransactionalProfessionalPreparationCommands(
            CreatePreparationTaskUseCase createDelegate,
            ChangePreparationTaskStatusUseCase statusDelegate,
            ConfirmPreparationReadyUseCase confirmationDelegate) {
        this.createDelegate = createDelegate;
        this.statusDelegate = statusDelegate;
        this.confirmationDelegate = confirmationDelegate;
    }

    @Override
    @Transactional
    public CreatePreparationTaskResult create(CreatePreparationTaskCommand command) {
        return createDelegate.create(command);
    }

    @Override
    @Transactional
    public PreparationTaskResult changeStatus(ChangePreparationTaskStatusCommand command) {
        return statusDelegate.changeStatus(command);
    }

    @Override
    @Transactional
    public PreparationReadyConfirmationResult confirm(ConfirmPreparationReadyCommand command) {
        return confirmationDelegate.confirm(command);
    }
}
