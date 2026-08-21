package backend.preparation.application.port.out;

import java.util.List;

import backend.preparation.domain.OrganizationId;

public interface ProfessionalContextSource {

    ProfessionalContextSnapshot loadCurrent();

    record ProfessionalContextSnapshot(
            long actorUserId,
            String displayName,
            List<ProfessionalOrganizationAccess> organizations) {

        public ProfessionalContextSnapshot {
            organizations = List.copyOf(organizations);
        }
    }

    record ProfessionalOrganizationAccess(
            OrganizationId organizationId,
            String displayName,
            List<PreparationCaseCapability> capabilities) {

        public ProfessionalOrganizationAccess {
            capabilities = List.copyOf(capabilities);
        }
    }
}
