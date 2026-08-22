package backend.preparation.application.port.out;

import java.util.Optional;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseClosure;
import backend.preparation.domain.PreparationCaseId;

public interface PreparationCaseClosureRepository {

    void add(PreparationCaseClosure closure);

    Optional<PreparationCaseClosure> findByCase(
            PreparationCaseId caseId,
            OrganizationId organizationId);
}
