package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;

import backend.preparation.domain.AttachmentMediaType;

public record ProfessionalAttachmentSnapshot(
        String displayFilename,
        AttachmentMediaType mediaType,
        long sizeBytes,
        Instant uploadedAt,
        Instant updatedAt) {

    public ProfessionalAttachmentSnapshot {
        if (displayFilename == null || displayFilename.isBlank()) {
            throw new IllegalArgumentException("Attachment display filename is required");
        }
        Objects.requireNonNull(mediaType, "Attachment media type is required");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Attachment size must be positive");
        }
        Objects.requireNonNull(uploadedAt, "Attachment upload timestamp is required");
        Objects.requireNonNull(updatedAt, "Attachment update timestamp is required");
    }

    @Override
    public String toString() {
        return "ProfessionalAttachmentSnapshot[metadata redacted]";
    }
}
