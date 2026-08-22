package backend.preparation.adapter.out.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import backend.preparation.application.PreparationWorkflowConflictException;
import backend.preparation.application.port.out.PreparationCaseClosureRepository;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseClosure;
import backend.preparation.domain.PreparationCaseId;

@Repository
public class JpaPreparationCaseClosureRepositoryAdapter
        implements PreparationCaseClosureRepository {

    private final SpringDataPreparationCaseClosureRepository repository;

    public JpaPreparationCaseClosureRepositoryAdapter(
            SpringDataPreparationCaseClosureRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PreparationCaseClosure closure) {
        try {
            repository.saveAndFlush(new PreparationCaseClosureJpaEntity(closure));
        } catch (DataIntegrityViolationException exception) {
            throw new PreparationWorkflowConflictException();
        }
    }

    @Override
    public Optional<PreparationCaseClosure> findByCase(
            PreparationCaseId caseId,
            OrganizationId organizationId) {
        return repository.findByPreparationCaseIdAndOrganizationId(
                        caseId.value(), organizationId.value())
                .map(entity -> new PreparationCaseClosure(
                        new OrganizationId(entity.organizationId()),
                        new PreparationCaseId(entity.preparationCaseId()),
                        entity.closedByUserId(),
                        entity.closedAt(),
                        entity.readyCaseRevision(),
                        entity.closedCaseRevision()));
    }
}
