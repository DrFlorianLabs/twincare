package backend.preparation.domain;

import java.util.Objects;
import java.util.UUID;

public record AttachmentId(UUID value) {

    public AttachmentId {
        Objects.requireNonNull(value, "Attachment identifier is required");
    }

    @Override
    public String toString() {
        return "AttachmentId[redacted]";
    }
}
