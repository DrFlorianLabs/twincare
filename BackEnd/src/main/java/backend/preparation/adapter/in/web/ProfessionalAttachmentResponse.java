package backend.preparation.adapter.in.web;

import java.time.Instant;

public record ProfessionalAttachmentResponse(
        boolean present,
        String displayFilename,
        String mediaType,
        Long sizeBytes,
        Instant uploadedAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "ProfessionalAttachmentResponse[metadata redacted]";
    }
}
