package backend.preparation.application.port.out;

import backend.preparation.application.port.in.PatientAttachmentUpload;
import backend.preparation.domain.AttachmentStorageKey;

public interface PrivateAttachmentStorage {

    StagedAttachment stage(PatientAttachmentUpload upload);

    void promote(StagedAttachment stagedAttachment);

    void discardStaged(StagedAttachment stagedAttachment);

    void deleteFinal(AttachmentStorageKey storageKey);

    StagedAttachmentDeletion stageDeletion(AttachmentStorageKey storageKey);

    void restoreStagedDeletion(StagedAttachmentDeletion deletion);

    void finalizeStagedDeletion(StagedAttachmentDeletion deletion);
}
