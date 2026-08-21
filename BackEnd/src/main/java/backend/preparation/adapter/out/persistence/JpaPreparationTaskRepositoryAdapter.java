package backend.preparation.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;

import backend.preparation.application.PreparationWorkflowConflictException;
import backend.preparation.application.port.out.PreparationTaskRepository;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationTask;
import backend.preparation.domain.PreparationTaskId;
import backend.preparation.domain.PreparationTaskStatus;

@Repository
public class JpaPreparationTaskRepositoryAdapter implements PreparationTaskRepository {

    private final SpringDataPreparationTaskRepository repository;
    private final PreparationTaskPersistenceMapper mapper = new PreparationTaskPersistenceMapper();

    public JpaPreparationTaskRepositoryAdapter(SpringDataPreparationTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PreparationTask task) {
        try {
            repository.saveAndFlush(mapper.toEntity(task));
        } catch (DataIntegrityViolationException exception) {
            throw new PreparationWorkflowConflictException();
        }
    }

    @Override
    public void update(PreparationTask task) {
        PreparationTaskJpaEntity managed = repository.findForUpdate(
                        task.id().value(),
                        task.preparationCaseId().value(),
                        task.organizationId().value())
                .orElseThrow(() -> new IllegalStateException("Preparation task disappeared during update"));
        managed.updateFrom(mapper.toEntity(task));
        repository.saveAndFlush(managed);
    }

    @Override
    public Optional<PreparationTask> findByIdForUpdate(
            PreparationTaskId taskId,
            PreparationCaseId caseId,
            OrganizationId organizationId) {
        return repository.findForUpdate(taskId.value(), caseId.value(), organizationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PreparationTask> findByCreationRequestId(
            UUID requestId,
            PreparationCaseId caseId,
            OrganizationId organizationId) {
        return repository.findByCreationRequestIdAndPreparationCaseIdAndOrganizationId(
                        requestId, caseId.value(), organizationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public long countAll(PreparationCaseId caseId, OrganizationId organizationId) {
        return repository.countByPreparationCaseIdAndOrganizationId(caseId.value(), organizationId.value());
    }

    @Override
    public long countOpen(PreparationCaseId caseId, OrganizationId organizationId) {
        return repository.countByPreparationCaseIdAndOrganizationIdAndStatus(
                caseId.value(), organizationId.value(), PreparationTaskStatus.OPEN);
    }

    @Override
    public List<PreparationTask> findAll(PreparationCaseId caseId, OrganizationId organizationId) {
        return repository
                .findAllByPreparationCaseIdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                        caseId.value(), organizationId.value())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
