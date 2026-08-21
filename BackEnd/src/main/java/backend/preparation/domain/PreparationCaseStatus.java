package backend.preparation.domain;

public enum PreparationCaseStatus {
    CREATED,
    PATIENT_STARTED,
    COLLECTING,
    READY_FOR_REVIEW,
    NEEDS_ACTION,
    READY_FOR_CONSULTATION,
    CLOSED,
    EXPIRED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CLOSED || this == EXPIRED || this == CANCELLED;
    }

    public boolean allowsPatientContribution() {
        return this == PATIENT_STARTED || this == COLLECTING || this == NEEDS_ACTION;
    }

    boolean canTransitionTo(PreparationCaseStatus target) {
        return switch (this) {
            case CREATED -> target == PATIENT_STARTED || target == EXPIRED || target == CANCELLED;
            case PATIENT_STARTED -> target == COLLECTING || target == EXPIRED || target == CANCELLED;
            case COLLECTING -> target == READY_FOR_REVIEW
                    || target == NEEDS_ACTION
                    || target == EXPIRED
                    || target == CANCELLED;
            case READY_FOR_REVIEW -> target == NEEDS_ACTION
                    || target == READY_FOR_CONSULTATION
                    || target == CANCELLED;
            case NEEDS_ACTION -> target == COLLECTING || target == CANCELLED;
            case READY_FOR_CONSULTATION -> target == CLOSED || target == CANCELLED;
            case CLOSED, EXPIRED, CANCELLED -> false;
        };
    }
}
