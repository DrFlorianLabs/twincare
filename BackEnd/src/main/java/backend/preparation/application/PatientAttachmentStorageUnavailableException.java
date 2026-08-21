package backend.preparation.application;

public final class PatientAttachmentStorageUnavailableException extends RuntimeException {

    public PatientAttachmentStorageUnavailableException(Throwable cause) {
        super("Patient attachment storage is unavailable", cause);
    }
}
