package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.AttachmentProvenance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "preparation_attachment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_preparation_attachment_case",
                        columnNames = { "preparation_case_id", "organization_id" }),
                @UniqueConstraint(
                        name = "uq_preparation_attachment_storage_key",
                        columnNames = "storage_key")
        })
class PreparationAttachmentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "preparation_case_id", nullable = false)
    private UUID preparationCaseId;

    @Column(name = "storage_key", nullable = false)
    private UUID storageKey;

    @Column(name = "display_filename", nullable = false, length = 200)
    private String displayFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
    private AttachmentMediaType mediaType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "sha256_digest", nullable = false, length = 64)
    private String sha256Digest;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false, length = 32, columnDefinition = "VARCHAR(32)")
    private AttachmentProvenance provenance;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PreparationAttachmentJpaEntity() {
    }

    PreparationAttachmentJpaEntity(
            UUID id,
            String organizationId,
            UUID preparationCaseId,
            UUID storageKey,
            String displayFilename,
            AttachmentMediaType mediaType,
            long sizeBytes,
            String sha256Digest,
            AttachmentProvenance provenance,
            Instant uploadedAt,
            Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.preparationCaseId = preparationCaseId;
        this.storageKey = storageKey;
        this.displayFilename = displayFilename;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256Digest = sha256Digest;
        this.provenance = provenance;
        this.uploadedAt = uploadedAt;
        this.updatedAt = updatedAt;
    }

    UUID id() {
        return id;
    }

    String organizationId() {
        return organizationId;
    }

    UUID preparationCaseId() {
        return preparationCaseId;
    }

    UUID storageKey() {
        return storageKey;
    }

    String displayFilename() {
        return displayFilename;
    }

    AttachmentMediaType mediaType() {
        return mediaType;
    }

    long sizeBytes() {
        return sizeBytes;
    }

    String sha256Digest() {
        return sha256Digest;
    }

    AttachmentProvenance provenance() {
        return provenance;
    }

    Instant uploadedAt() {
        return uploadedAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void updateFrom(PreparationAttachmentJpaEntity source) {
        if (!id.equals(source.id)
                || !organizationId.equals(source.organizationId)
                || !preparationCaseId.equals(source.preparationCaseId)
                || !uploadedAt.equals(source.uploadedAt)
                || provenance != source.provenance) {
            throw new IllegalArgumentException("Preparation attachment identity cannot change");
        }
        storageKey = source.storageKey;
        displayFilename = source.displayFilename;
        mediaType = source.mediaType;
        sizeBytes = source.sizeBytes;
        sha256Digest = source.sha256Digest;
        updatedAt = source.updatedAt;
    }
}
