package backend.preparation.application.port.out;

import backend.preparation.domain.OrganizationId;

public interface ProfessionalPreparationAuthorization {

    long requireAccess(OrganizationId organizationId, PreparationCaseCapability capability);
}
