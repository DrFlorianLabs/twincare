package backend.preparation.application;

public class InvalidPreparationCaseCommandException extends RuntimeException {

    public InvalidPreparationCaseCommandException() {
        super("Preparation case creation command is invalid");
    }
}
