package backend.preparation.adapter.out.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import backend.preparation.application.PreparationCaseConflictException;
import backend.preparation.application.port.out.PreparationCaseRepository;
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
            repository.saveAndFlush(mapper.toEntity(preparationCase));
        } catch (DataIntegrityViolationException exception) {
            throw new PreparationCaseConflictException();
        }
    }

    @Override
    public Optional<PreparationCase> findById(PreparationCaseId id, OrganizationId organizationId) {
        return repository.findByIdAndOrganizationId(id.value(), organizationId.value())
                .map(mapper::toDomain);
    }
}
