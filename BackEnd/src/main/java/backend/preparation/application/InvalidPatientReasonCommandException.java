package backend.preparation.application;

public final class InvalidPatientReasonCommandException extends RuntimeException {

    public InvalidPatientReasonCommandException() {
        super("Patient reason command is invalid");
    }
}
