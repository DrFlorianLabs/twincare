package backend.preparation.domain;

import java.util.Objects;
import java.util.UUID;

public record AttachmentStorageKey(UUID value) {

    public AttachmentStorageKey {
        Objects.requireNonNull(value, "Attachment storage key is required");
    }

    @Override
    public String toString() {
        return "AttachmentStorageKey[redacted]";
    }
}
