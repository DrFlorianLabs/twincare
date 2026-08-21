package backend.preparation.application.port.out;

import backend.preparation.domain.AttachmentStorageKey;

public record StagedAttachmentDeletion(AttachmentStorageKey storageKey, boolean existed) {

    @Override
    public String toString() {
        return "StagedAttachmentDeletion[storage key redacted, existed=" + existed + "]";
    }
}
