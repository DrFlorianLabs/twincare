package backend.preparation.application.port.in;

public interface ConfirmPreparationReadyUseCase {

    PreparationReadyConfirmationResult confirm(ConfirmPreparationReadyCommand command);
}
