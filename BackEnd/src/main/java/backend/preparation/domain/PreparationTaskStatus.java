package backend.preparation.domain;

public enum PreparationTaskStatus {
    OPEN,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this != OPEN;
    }
}
