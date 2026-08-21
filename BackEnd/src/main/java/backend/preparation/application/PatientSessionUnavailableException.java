package backend.preparation.application;

public final class PatientSessionUnavailableException extends RuntimeException {

    public PatientSessionUnavailableException() {
        super("Patient access session is unavailable");
    }
}
