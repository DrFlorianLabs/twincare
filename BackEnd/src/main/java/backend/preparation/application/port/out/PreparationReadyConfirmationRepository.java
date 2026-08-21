package backend.preparation.application.port.out;

import java.util.Optional;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationReadyConfirmation;

public interface PreparationReadyConfirmationRepository {

    void add(PreparationReadyConfirmation confirmation);

    Optional<PreparationReadyConfirmation> findByCase(
            PreparationCaseId caseId,
            OrganizationId organizationId);
}
