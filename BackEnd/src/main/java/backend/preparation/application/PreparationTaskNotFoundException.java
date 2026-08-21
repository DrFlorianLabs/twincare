package backend.preparation.application;

public final class PreparationTaskNotFoundException extends RuntimeException {

    public PreparationTaskNotFoundException() {
        super("Preparation task not found");
    }
}
