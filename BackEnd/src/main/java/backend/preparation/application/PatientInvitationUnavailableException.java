package backend.preparation.application;

public class PatientInvitationUnavailableException extends RuntimeException {

    public PatientInvitationUnavailableException() {
        super("Patient invitation is unavailable");
    }
}
