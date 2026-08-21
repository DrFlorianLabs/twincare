package backend.preparation.application.service;

import java.util.Objects;

import backend.preparation.application.port.in.DeletePatientAttachmentCommand;
import backend.preparation.application.port.in.DeletePatientAttachmentUseCase;
import backend.preparation.application.port.out.AttachmentStorageTransaction;
import backend.preparation.application.port.out.PreparationAttachmentRepository;

public final class DeletePatientAttachmentService implements DeletePatientAttachmentUseCase {

    private final PatientAttachmentScopeLock scopeLock;
    private final PreparationAttachmentRepository attachments;
    private final AttachmentStorageTransaction storageTransaction;

    public DeletePatientAttachmentService(
            PatientAttachmentScopeLock scopeLock,
            PreparationAttachmentRepository attachments,
            AttachmentStorageTransaction storageTransaction) {
        this.scopeLock = Objects.requireNonNull(scopeLock);
        this.attachments = Objects.requireNonNull(attachments);
        this.storageTransaction = Objects.requireNonNull(storageTransaction);
    }

    @Override
    public void delete(DeletePatientAttachmentCommand command) {
        var lockedScope = scopeLock.lock(command == null ? null : command.grant());
        lockedScope.attachment().ifPresent(attachment -> {
            storageTransaction.deleteAfterCommit(attachment.storageKey());
            attachments.delete(attachment);
            scopeLock.touchVisibleContent(lockedScope);
        });
    }
}
