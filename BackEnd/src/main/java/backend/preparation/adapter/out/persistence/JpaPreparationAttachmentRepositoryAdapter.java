package backend.preparation.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.domain.AttachmentStorageKey;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationAttachment;
import backend.preparation.domain.PreparationCaseId;

@Repository
public class JpaPreparationAttachmentRepositoryAdapter implements PreparationAttachmentRepository {

    private final SpringDataPreparationAttachmentRepository repository;
    private final PreparationAttachmentPersistenceMapper mapper = new PreparationAttachmentPersistenceMapper();

    public JpaPreparationAttachmentRepositoryAdapter(SpringDataPreparationAttachmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PreparationAttachment attachment) {
        repository.saveAndFlush(mapper.toEntity(attachment));
    }

    @Override
    public void update(PreparationAttachment attachment) {
        PreparationAttachmentJpaEntity managed = repository
                .findByCaseForUpdate(
                        attachment.preparationCaseId().value(),
                        attachment.organizationId().value())
                .orElseThrow(() -> new IllegalStateException("Preparation attachment disappeared during update"));
        managed.updateFrom(mapper.toEntity(attachment));
        repository.saveAndFlush(managed);
    }

    @Override
    public void delete(PreparationAttachment attachment) {
        PreparationAttachmentJpaEntity managed = repository
                .findByCaseForUpdate(
                        attachment.preparationCaseId().value(),
                        attachment.organizationId().value())
                .orElseThrow(() -> new IllegalStateException("Preparation attachment disappeared during deletion"));
        repository.delete(managed);
        repository.flush();
    }

    @Override
    public Optional<PreparationAttachment> findByCase(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId) {
        return repository.findByPreparationCaseIdAndOrganizationId(
                        preparationCaseId.value(), organizationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PreparationAttachment> findByCaseForUpdate(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId) {
        return repository.findByCaseForUpdate(preparationCaseId.value(), organizationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByStorageKey(AttachmentStorageKey storageKey) {
        return repository.existsByStorageKey(storageKey.value());
    }

    @Override
    public List<AttachmentStorageKey> findAllStorageKeys() {
        return repository.findAllStorageKeys().stream()
                .map(AttachmentStorageKey::new)
                .toList();
    }
}
