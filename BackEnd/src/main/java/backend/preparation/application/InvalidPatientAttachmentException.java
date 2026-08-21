package backend.preparation.application;

public final class InvalidPatientAttachmentException extends RuntimeException {

    public enum Reason {
        BAD_REQUEST,
        TOO_LARGE,
        UNSUPPORTED_MEDIA_TYPE
    }

    private final Reason reason;

    public InvalidPatientAttachmentException(Reason reason) {
        super("Patient attachment is invalid");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
