package backend.preparation.adapter.out.security;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.exception.UnauthorizedActionException;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.domain.OrganizationId;
import backend.service.SecurityHelperService;

@Component
public class JpaPreparationCaseAuthorization implements PreparationCaseAuthorization {

    private static final Set<OrganizationMembershipRole> CREATOR_ROLES = EnumSet.of(
            OrganizationMembershipRole.CLINICIAN,
            OrganizationMembershipRole.MEDICAL_ASSISTANT,
            OrganizationMembershipRole.ORGANIZATION_ADMIN);

    private final SecurityHelperService securityHelperService;
    private final OrganizationMembershipJpaRepository membershipRepository;

    public JpaPreparationCaseAuthorization(
            SecurityHelperService securityHelperService,
            OrganizationMembershipJpaRepository membershipRepository) {
        this.securityHelperService = securityHelperService;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public long requireStaffAccess(OrganizationId organizationId) {
        AppUtilisateur currentUser = securityHelperService.getCurrentUser();
        if (!isStaff(currentUser.getRole())
                || !membershipRepository.existsByOrganizationIdAndUserIdAndActiveTrueAndMembershipRoleIn(
                        organizationId.value(), currentUser.getId(), CREATOR_ROLES)) {
            throw new UnauthorizedActionException("Accès interdit à cette organisation");
        }
        return currentUser.getId();
    }

    private boolean isStaff(Role role) {
        return role == Role.MEDECIN || role == Role.ASSISTANT_MEDICAL || role == Role.ADMIN;
    }
}
