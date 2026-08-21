package backend.preparation.application;

public class PreparationAttachmentNotFoundException extends RuntimeException {

    public PreparationAttachmentNotFoundException() {
        super("Preparation attachment not found");
    }
}
