package backend.preparation.application;

public class PreparationCaseNotFoundException extends RuntimeException {

    public PreparationCaseNotFoundException() {
        super("Preparation case not found");
    }
}
