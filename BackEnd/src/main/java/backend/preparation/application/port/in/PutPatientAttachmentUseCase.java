package backend.preparation.application.port.in;

public interface PutPatientAttachmentUseCase {

    PutPatientAttachmentResult put(PutPatientAttachmentCommand command);
}
