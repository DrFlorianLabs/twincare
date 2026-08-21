package backend.preparation.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationTask;
import backend.preparation.domain.PreparationTaskId;

public interface PreparationTaskRepository {

    void add(PreparationTask task);

    void update(PreparationTask task);

    Optional<PreparationTask> findByIdForUpdate(
            PreparationTaskId taskId,
            PreparationCaseId caseId,
            OrganizationId organizationId);

    Optional<PreparationTask> findByCreationRequestId(
            UUID requestId,
            PreparationCaseId caseId,
            OrganizationId organizationId);

    long countAll(PreparationCaseId caseId, OrganizationId organizationId);

    long countOpen(PreparationCaseId caseId, OrganizationId organizationId);

    List<PreparationTask> findAll(PreparationCaseId caseId, OrganizationId organizationId);
}
