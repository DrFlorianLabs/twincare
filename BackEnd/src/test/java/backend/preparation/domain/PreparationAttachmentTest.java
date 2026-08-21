package backend.preparation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PreparationAttachmentTest {

    private static final Instant UPLOADED_AT = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void patientUploadHasExplicitProvenanceAndCanBeReplaced() {
        PreparationAttachment attachment = attachment();
        AttachmentStorageKey replacementKey = new AttachmentStorageKey(UUID.randomUUID());
        AttachmentDigest replacementDigest = new AttachmentDigest("b".repeat(64));

        attachment.replace(
                replacementKey,
                "replacement.jpg",
                AttachmentMediaType.JPEG,
                84,
                replacementDigest,
                UPLOADED_AT.plusSeconds(60));

        assertThat(attachment.provenance()).isEqualTo(AttachmentProvenance.PATIENT_UPLOADED);
        assertThat(attachment.storageKey()).isEqualTo(replacementKey);
        assertThat(attachment.mediaType()).isEqualTo(AttachmentMediaType.JPEG);
        assertThat(attachment.updatedAt()).isEqualTo(UPLOADED_AT.plusSeconds(60));
        assertThat(attachment.uploadedAt()).isEqualTo(UPLOADED_AT);
        assertThat(attachment.toString()).doesNotContain("replacement.jpg", replacementDigest.value());
    }

    @Test
    void exactPayloadComparisonSupportsIdempotentPut() {
        PreparationAttachment attachment = attachment();

        assertThat(attachment.hasSamePayload(
                "synthetic.pdf", AttachmentMediaType.PDF, 42, new AttachmentDigest("a".repeat(64))))
                .isTrue();
        assertThat(attachment.hasSamePayload(
                "other.pdf", AttachmentMediaType.PDF, 42, new AttachmentDigest("a".repeat(64))))
                .isFalse();
    }

    @Test
    void rejectsInvalidSizeFilenameAndTimeline() {
        assertThatThrownBy(() -> PreparationAttachment.uploadedByPatient(
                new AttachmentId(UUID.randomUUID()),
                new OrganizationId("synthetic-org"),
                new PreparationCaseId(UUID.randomUUID()),
                new AttachmentStorageKey(UUID.randomUUID()),
                " ",
                AttachmentMediaType.PDF,
                42,
                new AttachmentDigest("a".repeat(64)),
                UPLOADED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PreparationAttachment.uploadedByPatient(
                new AttachmentId(UUID.randomUUID()),
                new OrganizationId("synthetic-org"),
                new PreparationCaseId(UUID.randomUUID()),
                new AttachmentStorageKey(UUID.randomUUID()),
                "synthetic.pdf",
                AttachmentMediaType.PDF,
                PreparationAttachment.ABSOLUTE_MAX_SIZE_BYTES + 1,
                new AttachmentDigest("a".repeat(64)),
                UPLOADED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        PreparationAttachment attachment = attachment();
        assertThatThrownBy(() -> attachment.replace(
                new AttachmentStorageKey(UUID.randomUUID()),
                "replacement.pdf",
                AttachmentMediaType.PDF,
                42,
                new AttachmentDigest("b".repeat(64)),
                UPLOADED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAUnicodeFilenameBoundedByCodePoints() {
        String filename = "😀".repeat(196) + ".pdf";

        PreparationAttachment attachment = PreparationAttachment.uploadedByPatient(
                new AttachmentId(UUID.randomUUID()),
                new OrganizationId("synthetic-org"),
                new PreparationCaseId(UUID.randomUUID()),
                new AttachmentStorageKey(UUID.randomUUID()),
                filename,
                AttachmentMediaType.PDF,
                42,
                new AttachmentDigest("a".repeat(64)),
                UPLOADED_AT);

        assertThat(attachment.displayFilename()).isEqualTo(filename);
    }

    private PreparationAttachment attachment() {
        return PreparationAttachment.uploadedByPatient(
                new AttachmentId(UUID.randomUUID()),
                new OrganizationId("synthetic-org"),
                new PreparationCaseId(UUID.randomUUID()),
                new AttachmentStorageKey(UUID.randomUUID()),
                "synthetic.pdf",
                AttachmentMediaType.PDF,
                42,
                new AttachmentDigest("a".repeat(64)),
                UPLOADED_AT);
    }
}
