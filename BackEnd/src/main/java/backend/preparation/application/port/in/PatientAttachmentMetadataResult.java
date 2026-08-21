package backend.preparation.application.port.in;

import java.time.Instant;

import backend.preparation.domain.AttachmentMediaType;

public record PatientAttachmentMetadataResult(
        String displayFilename,
        AttachmentMediaType mediaType,
        long sizeBytes,
        Instant uploadedAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "PatientAttachmentMetadataResult[metadata redacted]";
    }
}
