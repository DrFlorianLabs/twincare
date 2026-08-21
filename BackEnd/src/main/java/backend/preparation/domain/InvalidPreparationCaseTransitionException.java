package backend.preparation.domain;

public class InvalidPreparationCaseTransitionException extends IllegalStateException {

    private final PreparationCaseStatus currentStatus;
    private final PreparationCaseStatus requestedStatus;

    public InvalidPreparationCaseTransitionException(
            PreparationCaseStatus currentStatus,
            PreparationCaseStatus requestedStatus) {
        super("Preparation case transition is not allowed: " + currentStatus + " -> " + requestedStatus);
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public PreparationCaseStatus currentStatus() {
        return currentStatus;
    }

    public PreparationCaseStatus requestedStatus() {
        return requestedStatus;
    }
}
