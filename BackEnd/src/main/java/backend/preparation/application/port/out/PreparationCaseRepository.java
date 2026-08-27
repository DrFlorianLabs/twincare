package backend.preparation.application.port.out;

import java.util.Optional;
import java.util.UUID;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;

public interface PreparationCaseRepository {

    void add(PreparationCase preparationCase);

    void add(
            PreparationCase preparationCase,
            UUID creationRequestId,
            PreparationCaseCreationFingerprint creationRequestFingerprint);

    long update(PreparationCase preparationCase);

    Optional<PreparationCase> findById(PreparationCaseId id, OrganizationId organizationId);

    Optional<PreparationCase> findByIdForUpdate(PreparationCaseId id, OrganizationId organizationId);

    Optional<StoredPreparationCaseCreation> findByCreationRequestId(
            OrganizationId organizationId,
            UUID creationRequestId);
}
