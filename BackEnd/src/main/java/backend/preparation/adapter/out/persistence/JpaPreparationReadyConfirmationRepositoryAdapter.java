package backend.preparation.adapter.out.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import backend.preparation.application.PreparationWorkflowConflictException;
import backend.preparation.application.port.out.PreparationReadyConfirmationRepository;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationReadyConfirmation;

@Repository
public class JpaPreparationReadyConfirmationRepositoryAdapter
        implements PreparationReadyConfirmationRepository {

    private final SpringDataPreparationReadyConfirmationRepository repository;

    public JpaPreparationReadyConfirmationRepositoryAdapter(
            SpringDataPreparationReadyConfirmationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PreparationReadyConfirmation confirmation) {
        try {
            repository.saveAndFlush(new PreparationReadyConfirmationJpaEntity(
                    confirmation.preparationCaseId().value(),
                    confirmation.organizationId().value(),
                    confirmation.confirmedByUserId(),
                    confirmation.confirmedAt(),
                    confirmation.ruleSetId(),
                    confirmation.ruleSetVersion(),
                    confirmation.reviewedCaseRevision(),
                    confirmation.reviewedReasonRecordedAt(),
                    confirmation.openTaskCount()));
        } catch (DataIntegrityViolationException exception) {
            throw new PreparationWorkflowConflictException();
        }
    }

    @Override
    public Optional<PreparationReadyConfirmation> findByCase(
            PreparationCaseId caseId,
            OrganizationId organizationId) {
        return repository.findByPreparationCaseIdAndOrganizationId(caseId.value(), organizationId.value())
                .map(entity -> new PreparationReadyConfirmation(
                        new OrganizationId(entity.organizationId()),
                        new PreparationCaseId(entity.preparationCaseId()),
                        entity.confirmedByUserId(),
                        entity.confirmedAt(),
                        entity.ruleSetId(),
                        entity.ruleSetVersion(),
                        entity.reviewedCaseRevision(),
                        entity.reviewedReasonRecordedAt(),
                        entity.openTaskCount()));
    }
}
