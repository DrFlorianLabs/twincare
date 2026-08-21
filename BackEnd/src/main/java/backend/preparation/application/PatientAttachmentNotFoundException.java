package backend.preparation.application;

public final class PatientAttachmentNotFoundException extends RuntimeException {

    public PatientAttachmentNotFoundException() {
        super("Patient attachment is unavailable");
    }
}
