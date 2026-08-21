package backend.preparation.application.port.in;

public record PutPatientAttachmentCommand(
        PatientAccessGrant grant,
        PatientAttachmentUpload upload) {

    @Override
    public String toString() {
        return "PutPatientAttachmentCommand[grant and content redacted]";
    }
}
