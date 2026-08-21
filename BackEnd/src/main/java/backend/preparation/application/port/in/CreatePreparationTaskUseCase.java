package backend.preparation.application.port.in;

public interface CreatePreparationTaskUseCase {

    CreatePreparationTaskResult create(CreatePreparationTaskCommand command);
}
