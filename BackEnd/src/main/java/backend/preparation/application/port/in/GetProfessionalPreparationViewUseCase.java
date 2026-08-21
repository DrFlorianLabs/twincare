package backend.preparation.application.port.in;

public interface GetProfessionalPreparationViewUseCase {

    ProfessionalPreparationViewResult get(GetProfessionalPreparationViewCommand command);
}
