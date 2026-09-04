package backend.clinicalcontext.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import backend.clinicalcontext.application.ClinicalFactConflictException;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ClinicalFactKind;
import backend.clinicalcontext.domain.ConsultationReasonFact;
import backend.clinicalcontext.domain.FactLifecycleStatus;

@Repository
public class JpaClinicalFactRepositoryAdapter implements ClinicalFactRepository {

    private final SpringDataClinicalFactRepository repository;
    private final ClinicalFactPersistenceMapper mapper = new ClinicalFactPersistenceMapper();

    public JpaClinicalFactRepositoryAdapter(SpringDataClinicalFactRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(ConsultationReasonFact fact) {
        try {
            repository.saveAndFlush(mapper.toEntity(fact));
        } catch (DataIntegrityViolationException exception) {
            throw new ClinicalFactConflictException();
        }
    }

    @Override
    public void update(ConsultationReasonFact fact) {
        ClinicalFactJpaEntity managed = repository.findByIdentityForUpdate(
                        fact.id().value(), fact.contextId().value(), fact.owner().value())
                .orElseThrow(() -> new IllegalStateException("Clinical fact disappeared during update"));
        managed.updateFrom(mapper.toEntity(fact));
        repository.saveAndFlush(managed);
    }

    @Override
    public Optional<ConsultationReasonFact> findCurrentConsultationReason(
            ClinicalContextId contextId,
            ClinicalContextOwner owner) {
        return repository.findByContextIdAndOwnerIdAndKindAndLifecycleStatus(
                        contextId.value(),
                        owner.value(),
                        ClinicalFactKind.CONSULTATION_REASON,
                        FactLifecycleStatus.CURRENT)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ConsultationReasonFact> findByIdForUpdate(
            ClinicalFactId factId,
            ClinicalContextId contextId,
            ClinicalContextOwner owner) {
        return repository.findByIdentityForUpdate(factId.value(), contextId.value(), owner.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<ConsultationReasonFact> findAllByContext(
            ClinicalContextId contextId,
            ClinicalContextOwner owner) {
        return repository.findAllByContext(contextId.value(), owner.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
