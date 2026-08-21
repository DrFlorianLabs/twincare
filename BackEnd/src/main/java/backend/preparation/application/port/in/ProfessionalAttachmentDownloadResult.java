package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.application.port.out.PreparedAttachmentContent;
import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.PreparationAttachment;

public record ProfessionalAttachmentDownloadResult(
        String displayFilename,
        AttachmentMediaType mediaType,
        long sizeBytes,
        PreparedAttachmentContent content) {

    public ProfessionalAttachmentDownloadResult {
        Objects.requireNonNull(displayFilename, "Attachment display filename is required");
        Objects.requireNonNull(mediaType, "Attachment media type is required");
        Objects.requireNonNull(content, "Prepared attachment content is required");
        if (displayFilename.isBlank()
                || displayFilename.codePointCount(0, displayFilename.length())
                        > PreparationAttachment.MAX_DISPLAY_FILENAME_LENGTH
                || displayFilename.codePoints().anyMatch(Character::isISOControl)
                || sizeBytes <= 0
                || sizeBytes > PreparationAttachment.ABSOLUTE_MAX_SIZE_BYTES
                || content.sizeBytes() != sizeBytes) {
            throw new IllegalArgumentException("Professional attachment download metadata is invalid");
        }
    }

    @Override
    public String toString() {
        return "ProfessionalAttachmentDownloadResult[metadata and content redacted]";
    }
}
