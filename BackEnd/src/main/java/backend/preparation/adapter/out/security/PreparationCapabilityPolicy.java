package backend.preparation.adapter.out.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import backend.entity.Role;
import backend.preparation.application.port.out.PreparationCaseCapability;

final class PreparationCapabilityPolicy {

    private PreparationCapabilityPolicy() {
    }

    static Set<PreparationCaseCapability> capabilitiesFor(
            Role platformRole,
            OrganizationMembershipRole membershipRole) {
        var capabilities = EnumSet.noneOf(PreparationCaseCapability.class);
        boolean clinicalPlatformRole = platformRole == Role.MEDECIN
                || platformRole == Role.ASSISTANT_MEDICAL;
        boolean clinicalMembership = membershipRole == OrganizationMembershipRole.CLINICIAN
                || membershipRole == OrganizationMembershipRole.MEDICAL_ASSISTANT;
        if (clinicalPlatformRole && clinicalMembership) {
            capabilities.add(PreparationCaseCapability.CREATE_PREPARATION_CASE);
            capabilities.add(PreparationCaseCapability.ISSUE_PATIENT_INVITATION);
            capabilities.add(PreparationCaseCapability.VIEW_PREPARATION);
            capabilities.add(PreparationCaseCapability.MANAGE_PREPARATION_TASKS);
            capabilities.add(PreparationCaseCapability.DOWNLOAD_ATTACHMENT);
        }
        if (platformRole == Role.MEDECIN
                && membershipRole == OrganizationMembershipRole.CLINICIAN) {
            capabilities.add(PreparationCaseCapability.CONFIRM_READY_FOR_CONSULTATION);
            capabilities.add(PreparationCaseCapability.CLOSE_PREPARATION_CASE);
            capabilities.add(PreparationCaseCapability.VIEW_PREPARATION_AUDIT);
        }
        return Collections.unmodifiableSet(capabilities);
    }
}
