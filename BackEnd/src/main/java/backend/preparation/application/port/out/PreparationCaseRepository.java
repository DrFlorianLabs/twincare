package backend.preparation.application.port.out;

import java.util.Optional;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;

public interface PreparationCaseRepository {

    void add(PreparationCase preparationCase);

    long update(PreparationCase preparationCase);

    Optional<PreparationCase> findById(PreparationCaseId id, OrganizationId organizationId);

    Optional<PreparationCase> findByIdForUpdate(PreparationCaseId id, OrganizationId organizationId);
}
