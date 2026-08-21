package backend.preparation.application;

public final class PreparationWorkflowConflictException extends RuntimeException {

    public PreparationWorkflowConflictException() {
        super("Preparation workflow is not available in its current state");
    }
}
