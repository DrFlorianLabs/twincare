package backend.preparation.application.port.in;

public interface ChangePreparationTaskStatusUseCase {

    PreparationTaskResult changeStatus(ChangePreparationTaskStatusCommand command);
}
