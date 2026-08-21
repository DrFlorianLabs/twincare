package backend.preparation.application.port.out;

import java.util.Optional;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public interface ProfessionalPreparationQueryRepository {

    ProfessionalPreparationListPageSnapshot findPage(
            OrganizationId organizationId,
            int page,
            int size);

    Optional<ProfessionalPreparationViewSnapshot> findView(
            OrganizationId organizationId,
            PreparationCaseId caseId);
}
