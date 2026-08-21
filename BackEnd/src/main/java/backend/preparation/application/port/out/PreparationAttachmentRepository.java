package backend.preparation.application.port.out;

import java.util.List;
import java.util.Optional;

import backend.preparation.domain.AttachmentStorageKey;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationAttachment;
import backend.preparation.domain.PreparationCaseId;

public interface PreparationAttachmentRepository {

    void add(PreparationAttachment attachment);

    void update(PreparationAttachment attachment);

    void delete(PreparationAttachment attachment);

    Optional<PreparationAttachment> findByCase(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    Optional<PreparationAttachment> findByCaseForUpdate(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    boolean existsByStorageKey(AttachmentStorageKey storageKey);

    List<AttachmentStorageKey> findAllStorageKeys();
}
