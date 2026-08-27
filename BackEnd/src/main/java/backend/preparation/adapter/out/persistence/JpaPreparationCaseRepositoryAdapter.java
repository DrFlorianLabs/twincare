package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import backend.preparation.application.PreparationCaseConflictException;
import backend.preparation.application.port.out.PreparationCaseCreationFingerprint;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.StoredPreparationCaseCreation;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;

@Repository
public class JpaPreparationCaseRepositoryAdapter implements PreparationCaseRepository {

    private final SpringDataPreparationCaseRepository repository;
    private final PreparationCasePersistenceMapper mapper = new PreparationCasePersistenceMapper();

    public JpaPreparationCaseRepositoryAdapter(SpringDataPreparationCaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PreparationCase preparationCase) {
        addEntity(preparationCase, mapper.toEntity(preparationCase));
    }

    @Override
    public void add(
            PreparationCase preparationCase,
            UUID creationRequestId,
            PreparationCaseCreationFingerprint creationRequestFingerprint) {
        addEntity(preparationCase, mapper.toEntity(
                preparationCase,
                creationRequestId,
                creationRequestFingerprint.value()));
    }

    private void addEntity(PreparationCase preparationCase, PreparationCaseJpaEntity entity) {
        if (repository.lockOrganizationForCaseCreation(preparationCase.organizationId().value()).isEmpty()) {
            throw new PreparationCaseConflictException();
        }
        if (entity.creationRequestId() != null
                && repository.existsByOrganizationIdAndCreationRequestId(
                        preparationCase.organizationId().value(), entity.creationRequestId())) {
            throw new PreparationCaseConflictException();
        }
        Optional<String> appointmentReference = preparationCase.appointmentReference()
                .map(reference -> reference.value());
        if (appointmentReference.filter(reference -> repository
                .existsByOrganizationIdAndAppointmentReference(
                        preparationCase.organizationId().value(), reference)).isPresent()) {
            throw new PreparationCaseConflictException();
        }
        if (preparationCase.scheduledAt().filter(scheduledAt -> repository
                .existsByOrganizationIdAndPatientReferenceAndScheduledAt(
                        preparationCase.organizationId().value(),
                        preparationCase.patientReference().value(),
                        scheduledAt)).isPresent()) {
            throw new PreparationCaseConflictException();
        }
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new PreparationCaseConflictException();
        }
    }

    @Override
    public long update(PreparationCase preparationCase) {
        PreparationCaseJpaEntity managed = repository
                .findByIdAndOrganizationId(
                        preparationCase.id().value(),
                        preparationCase.organizationId().value())
                .orElseThrow(() -> new IllegalStateException("Preparation case disappeared during update"));
        long previousVersion = managed.version();
        managed.updateFrom(mapper.toEntity(preparationCase));
        repository.saveAndFlush(managed);
        if (managed.version() == previousVersion
                && repository.forceIncrementVersion(
                        preparationCase.id().value(),
                        preparationCase.organizationId().value(),
                        previousVersion) != 1) {
            throw new IllegalStateException("Preparation case revision could not be advanced");
        }
        return previousVersion + 1;
    }

    @Override
    public Optional<PreparationCase> findById(PreparationCaseId id, OrganizationId organizationId) {
        return repository.findByIdAndOrganizationId(id.value(), organizationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PreparationCase> findByIdForUpdate(PreparationCaseId id, OrganizationId organizationId) {
        return repository.findByIdAndOrganizationIdForUpdate(id.value(), organizationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<StoredPreparationCaseCreation> findByCreationRequestId(
            OrganizationId organizationId,
            UUID creationRequestId) {
        return repository.findByOrganizationIdAndCreationRequestId(
                        organizationId.value(), creationRequestId)
                .map(entity -> new StoredPreparationCaseCreation(
                        mapper.toDomain(entity),
                        new PreparationCaseCreationFingerprint(entity.creationRequestFingerprint())));
    }
}
