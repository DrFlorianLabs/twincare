package backend.preparation.adapter.in.web;

import java.time.Instant;

public record PatientAttachmentResponse(
        String displayFilename,
        String mediaType,
        long sizeBytes,
        Instant uploadedAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "PatientAttachmentResponse[metadata redacted]";
    }
}
