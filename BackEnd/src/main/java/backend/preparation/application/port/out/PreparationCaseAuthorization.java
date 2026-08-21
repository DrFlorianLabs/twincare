package backend.preparation.application.port.out;

import backend.preparation.domain.OrganizationId;

public interface PreparationCaseAuthorization {

    void requireCreatePermission(OrganizationId organizationId);
}
