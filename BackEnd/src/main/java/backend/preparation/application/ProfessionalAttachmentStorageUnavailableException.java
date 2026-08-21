package backend.preparation.application;

public class ProfessionalAttachmentStorageUnavailableException extends RuntimeException {

    public ProfessionalAttachmentStorageUnavailableException(Throwable cause) {
        super("Professional preparation attachment storage is unavailable", cause);
    }
}
