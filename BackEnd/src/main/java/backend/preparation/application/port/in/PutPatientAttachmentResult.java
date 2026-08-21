package backend.preparation.application.port.in;

import java.time.Instant;

import backend.preparation.domain.AttachmentMediaType;

public record PutPatientAttachmentResult(
        String displayFilename,
        AttachmentMediaType mediaType,
        long sizeBytes,
        Instant uploadedAt,
        Instant updatedAt,
        boolean created,
        boolean changed) {

    @Override
    public String toString() {
        return "PutPatientAttachmentResult[metadata redacted, created="
                + created + ", changed=" + changed + "]";
    }
}
