package backend.preparation.adapter.out.persistence;

import backend.preparation.domain.AttachmentDigest;
import backend.preparation.domain.AttachmentId;
import backend.preparation.domain.AttachmentStorageKey;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationAttachment;
import backend.preparation.domain.PreparationCaseId;

final class PreparationAttachmentPersistenceMapper {

    PreparationAttachmentJpaEntity toEntity(PreparationAttachment attachment) {
        return new PreparationAttachmentJpaEntity(
                attachment.id().value(),
                attachment.organizationId().value(),
                attachment.preparationCaseId().value(),
                attachment.storageKey().value(),
                attachment.displayFilename(),
                attachment.mediaType(),
                attachment.sizeBytes(),
                attachment.digest().value(),
                attachment.provenance(),
                attachment.uploadedAt(),
                attachment.updatedAt());
    }

    PreparationAttachment toDomain(PreparationAttachmentJpaEntity entity) {
        return PreparationAttachment.restore(
                new AttachmentId(entity.id()),
                new OrganizationId(entity.organizationId()),
                new PreparationCaseId(entity.preparationCaseId()),
                new AttachmentStorageKey(entity.storageKey()),
                entity.displayFilename(),
                entity.mediaType(),
                entity.sizeBytes(),
                new AttachmentDigest(entity.sha256Digest()),
                entity.provenance(),
                entity.uploadedAt(),
                entity.updatedAt());
    }
}
