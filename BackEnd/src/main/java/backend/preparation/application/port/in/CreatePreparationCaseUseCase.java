package backend.preparation.application.port.in;

public interface CreatePreparationCaseUseCase {

    CreatePreparationCaseResult create(CreatePreparationCaseCommand command);
}
