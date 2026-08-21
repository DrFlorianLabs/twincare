package backend.preparation.application.service;

import java.util.Objects;

import backend.preparation.application.PreparationAttachmentNotFoundException;
import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentCommand;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentDownloadResult;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparedAttachmentContent;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PrivateAttachmentContentReader;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;

public final class DownloadProfessionalAttachmentService implements DownloadProfessionalAttachmentUseCase {

    private final ProfessionalPreparationAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PreparationAttachmentRepository attachments;
    private final PrivateAttachmentContentReader contentReader;

    public DownloadProfessionalAttachmentService(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationAttachmentRepository attachments,
            PrivateAttachmentContentReader contentReader) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.attachments = Objects.requireNonNull(attachments);
        this.contentReader = Objects.requireNonNull(contentReader);
    }

    @Override
    public ProfessionalAttachmentDownloadResult download(DownloadProfessionalAttachmentCommand command) {
        Objects.requireNonNull(command, "Professional attachment download command is required");
        authorization.requireAccess(
                command.organizationId(),
                PreparationCaseCapability.DOWNLOAD_ATTACHMENT);
        var preparationCase = preparationCases
                .findByIdForUpdate(command.preparationCaseId(), command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);
        var attachment = attachments
                .findByCaseForUpdate(preparationCase.id(), preparationCase.organizationId())
                .orElseThrow(PreparationAttachmentNotFoundException::new);
        PreparedAttachmentContent content;
        content = contentReader.openVerified(
                attachment.storageKey(),
                attachment.sizeBytes(),
                attachment.digest());
        try {
            return new ProfessionalAttachmentDownloadResult(
                    attachment.displayFilename(),
                    attachment.mediaType(),
                    attachment.sizeBytes(),
                    content);
        } catch (RuntimeException exception) {
            try {
                content.close();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }
}
