package backend.preparation.adapter.out.security;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.dao.AppUtilisateurRepository;
import backend.exception.UnauthorizedActionException;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.OrganizationId;
import backend.service.SecurityHelperService;

@Component
public class JpaPreparationCaseAuthorization implements ProfessionalPreparationAuthorization {

    private final SecurityHelperService securityHelperService;
    private final OrganizationMembershipJpaRepository membershipRepository;
    private final AppUtilisateurRepository userRepository;

    public JpaPreparationCaseAuthorization(
            SecurityHelperService securityHelperService,
            OrganizationMembershipJpaRepository membershipRepository,
            AppUtilisateurRepository userRepository) {
        this.securityHelperService = securityHelperService;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Override
    public long requireAccess(OrganizationId organizationId, PreparationCaseCapability capability) {
        AppUtilisateur currentUser = securityHelperService.getCurrentUser();
        if (currentUser.getRole() != Role.MEDECIN
                && currentUser.getRole() != Role.ASSISTANT_MEDICAL) {
            throw new UnauthorizedActionException("Accès interdit à cette organisation");
        }
        var membership = membershipRepository.findByOrganizationIdAndUserIdAndActiveTrue(
                organizationId.value(), currentUser.getId());
        if (membership.isEmpty()
                || !PreparationCapabilityPolicy.capabilitiesFor(
                        currentUser.getRole(), membership.get().membershipRole()).contains(capability)) {
            throw new UnauthorizedActionException("Accès interdit à cette organisation");
        }
        return currentUser.getId();
    }

    @Override
    public void requireAssignableStaff(OrganizationId organizationId, long userId) {
        if (userId <= 0) {
            throw new UnauthorizedActionException("Accès interdit à cette organisation");
        }
        Role platformRole = userRepository.findById(userId)
                .map(AppUtilisateur::getRole)
                .orElseThrow(() -> new UnauthorizedActionException("Accès interdit à cette organisation"));
        if ((platformRole != Role.MEDECIN && platformRole != Role.ASSISTANT_MEDICAL)
                || !membershipRepository.existsByOrganizationIdAndUserIdAndActiveTrueAndMembershipRoleIn(
                        organizationId.value(),
                        userId,
                        EnumSet.of(
                                OrganizationMembershipRole.CLINICIAN,
                                OrganizationMembershipRole.MEDICAL_ASSISTANT))) {
            throw new UnauthorizedActionException("Accès interdit à cette organisation");
        }
    }
}
