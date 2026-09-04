package backend.clinicalcontext.application.port.in;

public interface ReviewClinicalFactUseCase {

    ReviewClinicalFactResult review(ReviewClinicalFactCommand command);
}
