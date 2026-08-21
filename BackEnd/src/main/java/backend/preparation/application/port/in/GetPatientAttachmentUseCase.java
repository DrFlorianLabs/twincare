package backend.preparation.application.port.in;

public interface GetPatientAttachmentUseCase {

    PatientAttachmentMetadataResult get(GetPatientAttachmentCommand command);
}
