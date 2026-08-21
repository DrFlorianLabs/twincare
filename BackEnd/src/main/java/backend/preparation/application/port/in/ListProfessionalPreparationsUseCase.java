package backend.preparation.application.port.in;

public interface ListProfessionalPreparationsUseCase {

    ProfessionalPreparationPageResult list(ListProfessionalPreparationsCommand command);
}
