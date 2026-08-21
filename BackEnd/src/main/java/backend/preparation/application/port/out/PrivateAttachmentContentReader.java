package backend.preparation.application.port.out;

import backend.preparation.domain.AttachmentDigest;
import backend.preparation.domain.AttachmentStorageKey;

public interface PrivateAttachmentContentReader {

    PreparedAttachmentContent openVerified(
            AttachmentStorageKey storageKey,
            long expectedSizeBytes,
            AttachmentDigest expectedDigest);
}
