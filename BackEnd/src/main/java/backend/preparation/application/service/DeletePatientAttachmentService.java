package backend.preparation.application.service;

import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.port.in.DeletePatientAttachmentCommand;
import backend.preparation.application.port.in.DeletePatientAttachmentUseCase;
import backend.preparation.application.port.out.AttachmentStorageTransaction;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubject;
import backend.preparation.domain.PreparationAuditSubjectType;

public final class DeletePatientAttachmentService implements DeletePatientAttachmentUseCase {

    private final PatientAttachmentScopeLock scopeLock;
    private final PreparationAttachmentRepository attachments;
    private final AttachmentStorageTransaction storageTransaction;
    private final PreparationAuditEventStore auditEvents;

    public DeletePatientAttachmentService(
            PatientAttachmentScopeLock scopeLock,
            PreparationAttachmentRepository attachments,
            AttachmentStorageTransaction storageTransaction,
            PreparationAuditEventStore auditEvents) {
        this.scopeLock = Objects.requireNonNull(scopeLock);
        this.attachments = Objects.requireNonNull(attachments);
        this.storageTransaction = Objects.requireNonNull(storageTransaction);
        this.auditEvents = Objects.requireNonNull(auditEvents);
    }

    @Override
    public void delete(DeletePatientAttachmentCommand command) {
        var lockedScope = scopeLock.lock(command == null ? null : command.grant());
        lockedScope.attachment().ifPresent(attachment -> {
            storageTransaction.deleteAfterCommit(attachment.storageKey());
            attachments.delete(attachment);
            long persistedRevision = scopeLock.touchVisibleContent(lockedScope);
            auditEvents.append(PreparationAuditEvent.patient(
                    attachment.organizationId(),
                    attachment.preparationCaseId(),
                    PreparationAuditEventType.ATTACHMENT_DELETED,
                    lockedScope.now(),
                    persistedRevision,
                    Optional.of(new PreparationAuditSubject(
                            PreparationAuditSubjectType.ATTACHMENT,
                            attachment.id().value()))));
        });
    }
}
