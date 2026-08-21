package backend.preparation.application;

public class PreparationCaseConflictException extends RuntimeException {

    public PreparationCaseConflictException() {
        super("A preparation case conflicts with an existing consultation preparation");
    }
}
