package backend.preparation.application.port.in;

public interface DownloadProfessionalAttachmentUseCase {

    ProfessionalAttachmentDownloadResult download(DownloadProfessionalAttachmentCommand command);
}
