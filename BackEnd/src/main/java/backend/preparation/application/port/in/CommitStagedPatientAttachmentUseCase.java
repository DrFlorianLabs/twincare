package backend.preparation.application.port.in;

public interface CommitStagedPatientAttachmentUseCase {

    PutPatientAttachmentResult commit(CommitStagedPatientAttachmentCommand command);
}
