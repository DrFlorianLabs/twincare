package backend.preparation.application.service;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

import backend.preparation.application.InvalidPatientAttachmentException;
import backend.preparation.application.port.in.CommitStagedPatientAttachmentCommand;
import backend.preparation.application.port.in.CommitStagedPatientAttachmentUseCase;
import backend.preparation.application.port.in.PutPatientAttachmentResult;
import backend.preparation.application.port.out.AttachmentIdGenerator;
import backend.preparation.application.port.out.AttachmentStorageTransaction;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.StagedAttachment;
import backend.preparation.domain.AttachmentStorageKey;
import backend.preparation.domain.PreparationAttachment;

public final class CommitStagedPatientAttachmentService implements CommitStagedPatientAttachmentUseCase {

    private final PatientAttachmentScopeLock scopeLock;
    private final PreparationAttachmentRepository attachments;
    private final AttachmentIdGenerator idGenerator;
    private final AttachmentStorageTransaction storageTransaction;

    public CommitStagedPatientAttachmentService(
            PatientAttachmentScopeLock scopeLock,
            PreparationAttachmentRepository attachments,
            AttachmentIdGenerator idGenerator,
            AttachmentStorageTransaction storageTransaction) {
        this.scopeLock = Objects.requireNonNull(scopeLock);
        this.attachments = Objects.requireNonNull(attachments);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.storageTransaction = Objects.requireNonNull(storageTransaction);
    }

    @Override
    public PutPatientAttachmentResult commit(CommitStagedPatientAttachmentCommand command) {
        if (command == null || command.stagedAttachment() == null) {
            throw new InvalidPatientAttachmentException(InvalidPatientAttachmentException.Reason.BAD_REQUEST);
        }
        var lockedScope = scopeLock.lock(command.grant());
        StagedAttachment staged = command.stagedAttachment();
        PreparationAttachment existing = lockedScope.attachment().orElse(null);

        if (existing != null && existing.hasSamePayload(
                staged.displayFilename(), staged.mediaType(), staged.sizeBytes(), staged.digest())) {
            return result(existing, false, false);
        }

        storageTransaction.promoteForCommit(staged);
        var persistedAt = lockedScope.now().truncatedTo(ChronoUnit.MICROS);
        if (existing == null) {
            PreparationAttachment created = PreparationAttachment.uploadedByPatient(
                    idGenerator.nextId(),
                    lockedScope.preparationCase().organizationId(),
                    lockedScope.preparationCase().id(),
                    staged.storageKey(),
                    staged.displayFilename(),
                    staged.mediaType(),
                    staged.sizeBytes(),
                    staged.digest(),
                    persistedAt);
            attachments.add(created);
            scopeLock.touchVisibleContent(lockedScope);
            return result(created, true, true);
        }

        AttachmentStorageKey replacedStorageKey = existing.storageKey();
        storageTransaction.deleteAfterCommit(replacedStorageKey);
        existing.replace(
                staged.storageKey(),
                staged.displayFilename(),
                staged.mediaType(),
                staged.sizeBytes(),
                staged.digest(),
                persistedAt);
        attachments.update(existing);
        scopeLock.touchVisibleContent(lockedScope);
        return result(existing, false, true);
    }

    private PutPatientAttachmentResult result(
            PreparationAttachment attachment,
            boolean created,
            boolean changed) {
        return new PutPatientAttachmentResult(
                attachment.displayFilename(),
                attachment.mediaType(),
                attachment.sizeBytes(),
                attachment.uploadedAt(),
                attachment.updatedAt(),
                created,
                changed);
    }
}
