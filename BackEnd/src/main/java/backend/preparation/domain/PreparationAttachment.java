package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;

public final class PreparationAttachment {

    public static final long ABSOLUTE_MAX_SIZE_BYTES = 10L * 1024L * 1024L;
    public static final int MAX_DISPLAY_FILENAME_LENGTH = 200;

    private final AttachmentId id;
    private final OrganizationId organizationId;
    private final PreparationCaseId preparationCaseId;
    private final AttachmentProvenance provenance;
    private final Instant uploadedAt;

    private AttachmentStorageKey storageKey;
    private String displayFilename;
    private AttachmentMediaType mediaType;
    private long sizeBytes;
    private AttachmentDigest digest;
    private Instant updatedAt;

    private PreparationAttachment(
            AttachmentId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            AttachmentStorageKey storageKey,
            String displayFilename,
            AttachmentMediaType mediaType,
            long sizeBytes,
            AttachmentDigest digest,
            AttachmentProvenance provenance,
            Instant uploadedAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Attachment identifier is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization identifier is required");
        this.preparationCaseId = Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        this.storageKey = Objects.requireNonNull(storageKey, "Attachment storage key is required");
        this.displayFilename = requireDisplayFilename(displayFilename);
        this.mediaType = Objects.requireNonNull(mediaType, "Attachment media type is required");
        this.sizeBytes = requireSize(sizeBytes);
        this.digest = Objects.requireNonNull(digest, "Attachment digest is required");
        this.provenance = Objects.requireNonNull(provenance, "Attachment provenance is required");
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "Attachment upload timestamp is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Attachment update timestamp is required");
        if (updatedAt.isBefore(uploadedAt)) {
            throw new IllegalArgumentException("Attachment update cannot precede upload");
        }
    }

    public static PreparationAttachment uploadedByPatient(
            AttachmentId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            AttachmentStorageKey storageKey,
            String displayFilename,
            AttachmentMediaType mediaType,
            long sizeBytes,
            AttachmentDigest digest,
            Instant uploadedAt) {
        return new PreparationAttachment(
                id,
                organizationId,
                preparationCaseId,
                storageKey,
                displayFilename,
                mediaType,
                sizeBytes,
                digest,
                AttachmentProvenance.PATIENT_UPLOADED,
                uploadedAt,
                uploadedAt);
    }

    public static PreparationAttachment restore(
            AttachmentId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            AttachmentStorageKey storageKey,
            String displayFilename,
            AttachmentMediaType mediaType,
            long sizeBytes,
            AttachmentDigest digest,
            AttachmentProvenance provenance,
            Instant uploadedAt,
            Instant updatedAt) {
        return new PreparationAttachment(
                id,
                organizationId,
                preparationCaseId,
                storageKey,
                displayFilename,
                mediaType,
                sizeBytes,
                digest,
                provenance,
                uploadedAt,
                updatedAt);
    }

    public boolean hasSamePayload(
            String candidateDisplayFilename,
            AttachmentMediaType candidateMediaType,
            long candidateSizeBytes,
            AttachmentDigest candidateDigest) {
        return displayFilename.equals(candidateDisplayFilename)
                && mediaType == candidateMediaType
                && sizeBytes == candidateSizeBytes
                && digest.equals(candidateDigest);
    }

    public void replace(
            AttachmentStorageKey replacementStorageKey,
            String replacementDisplayFilename,
            AttachmentMediaType replacementMediaType,
            long replacementSizeBytes,
            AttachmentDigest replacementDigest,
            Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Attachment replacement timestamp is required");
        if (occurredAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Attachment replacement cannot precede the previous update");
        }
        storageKey = Objects.requireNonNull(replacementStorageKey, "Replacement storage key is required");
        displayFilename = requireDisplayFilename(replacementDisplayFilename);
        mediaType = Objects.requireNonNull(replacementMediaType, "Replacement media type is required");
        sizeBytes = requireSize(replacementSizeBytes);
        digest = Objects.requireNonNull(replacementDigest, "Replacement digest is required");
        updatedAt = occurredAt;
    }

    private static String requireDisplayFilename(String value) {
        Objects.requireNonNull(value, "Attachment display filename is required");
        if (value.isBlank()
                || value.codePointCount(0, value.length()) > MAX_DISPLAY_FILENAME_LENGTH
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Attachment display filename is invalid");
        }
        return value;
    }

    private static long requireSize(long value) {
        if (value <= 0 || value > ABSOLUTE_MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Attachment size is invalid");
        }
        return value;
    }

    public AttachmentId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public PreparationCaseId preparationCaseId() {
        return preparationCaseId;
    }

    public AttachmentStorageKey storageKey() {
        return storageKey;
    }

    public String displayFilename() {
        return displayFilename;
    }

    public AttachmentMediaType mediaType() {
        return mediaType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public AttachmentDigest digest() {
        return digest;
    }

    public AttachmentProvenance provenance() {
        return provenance;
    }

    public Instant uploadedAt() {
        return uploadedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "PreparationAttachment[identity and content redacted]";
    }
}
