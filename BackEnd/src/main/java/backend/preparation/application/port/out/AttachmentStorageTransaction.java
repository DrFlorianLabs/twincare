package backend.preparation.application.port.out;

import backend.preparation.domain.AttachmentStorageKey;

public interface AttachmentStorageTransaction {

    void promoteForCommit(StagedAttachment stagedAttachment);

    void deleteAfterCommit(AttachmentStorageKey storageKey);
}
