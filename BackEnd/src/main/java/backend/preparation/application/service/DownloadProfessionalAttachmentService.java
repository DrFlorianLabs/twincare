package backend.preparation.application.service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.PreparationAttachmentNotFoundException;
import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentCommand;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentDownloadResult;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparedAttachmentContent;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PrivateAttachmentContentReader;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubject;
import backend.preparation.domain.PreparationAuditSubjectType;

public final class DownloadProfessionalAttachmentService implements DownloadProfessionalAttachmentUseCase {

    private final ProfessionalPreparationAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PreparationAttachmentRepository attachments;
    private final PrivateAttachmentContentReader contentReader;
    private final PreparationAuditEventStore auditEvents;
    private final Clock clock;

    public DownloadProfessionalAttachmentService(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationAttachmentRepository attachments,
            PrivateAttachmentContentReader contentReader,
            PreparationAuditEventStore auditEvents,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.attachments = Objects.requireNonNull(attachments);
        this.contentReader = Objects.requireNonNull(contentReader);
        this.auditEvents = Objects.requireNonNull(auditEvents);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ProfessionalAttachmentDownloadResult download(DownloadProfessionalAttachmentCommand command) {
        Objects.requireNonNull(command, "Professional attachment download command is required");
        long actorUserId = authorization.requireAccess(
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
            var result = new ProfessionalAttachmentDownloadResult(
                    attachment.displayFilename(),
                    attachment.mediaType(),
                    attachment.sizeBytes(),
                    content);
            auditEvents.append(PreparationAuditEvent.staff(
                    preparationCase.organizationId(),
                    preparationCase.id(),
                    PreparationAuditEventType.ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED,
                    actorUserId,
                    clock.instant(),
                    preparationCase.revision(),
                    Optional.of(new PreparationAuditSubject(
                            PreparationAuditSubjectType.ATTACHMENT,
                            attachment.id().value()))));
            return result;
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
