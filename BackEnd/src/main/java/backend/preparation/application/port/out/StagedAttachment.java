package backend.preparation.application.port.out;

import java.util.Objects;

import backend.preparation.domain.AttachmentDigest;
import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.AttachmentStorageKey;

public record StagedAttachment(
        AttachmentStorageKey storageKey,
        String displayFilename,
        AttachmentMediaType mediaType,
        long sizeBytes,
        AttachmentDigest digest) {

    public StagedAttachment {
        Objects.requireNonNull(storageKey, "Staged attachment storage key is required");
        Objects.requireNonNull(displayFilename, "Staged attachment filename is required");
        Objects.requireNonNull(mediaType, "Staged attachment media type is required");
        Objects.requireNonNull(digest, "Staged attachment digest is required");
    }

    @Override
    public String toString() {
        return "StagedAttachment[content and storage key redacted]";
    }
}
