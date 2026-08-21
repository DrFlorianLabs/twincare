package backend.preparation.application;

public class PatientInvitationConflictException extends RuntimeException {

    public PatientInvitationConflictException() {
        super("Patient invitation operation conflicts with preparation state");
    }
}
