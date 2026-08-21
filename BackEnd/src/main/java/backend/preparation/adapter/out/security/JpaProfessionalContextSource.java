package backend.preparation.adapter.out.security;

import java.util.List;

import org.springframework.stereotype.Component;

import backend.entity.Role;
import backend.exception.UnauthorizedActionException;
import backend.preparation.application.port.out.ProfessionalContextSource;
import backend.preparation.domain.OrganizationId;
import backend.service.SecurityHelperService;

@Component
public class JpaProfessionalContextSource implements ProfessionalContextSource {

    private final SecurityHelperService securityHelperService;
    private final OrganizationMembershipJpaRepository memberships;

    public JpaProfessionalContextSource(
            SecurityHelperService securityHelperService,
            OrganizationMembershipJpaRepository memberships) {
        this.securityHelperService = securityHelperService;
        this.memberships = memberships;
    }

    @Override
    public ProfessionalContextSnapshot loadCurrent() {
        var currentUser = securityHelperService.getCurrentUser();
        if (currentUser.getRole() != Role.MEDECIN
                && currentUser.getRole() != Role.ASSISTANT_MEDICAL) {
            throw new UnauthorizedActionException("Accès réservé aux professionnels cliniques");
        }

        List<ProfessionalOrganizationAccess> organizations = memberships
                .findActiveOrganizationsForUser(currentUser.getId())
                .stream()
                .map(membership -> accessFor(currentUser.getRole(), membership))
                .filter(access -> !access.capabilities().isEmpty())
                .toList();

        return new ProfessionalContextSnapshot(
                currentUser.getId(),
                (currentUser.getPrenom() + " " + currentUser.getNom()).trim(),
                organizations);
    }

    private ProfessionalOrganizationAccess accessFor(
            Role platformRole,
            ActiveOrganizationMembershipProjection membership) {
        OrganizationMembershipRole membershipRole;
        try {
            membershipRole = OrganizationMembershipRole.valueOf(membership.getMembershipRole());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported organization membership role", exception);
        }

        var capabilities = PreparationCapabilityPolicy.capabilitiesFor(platformRole, membershipRole);
        return new ProfessionalOrganizationAccess(
                new OrganizationId(membership.getOrganizationId()),
                membership.getDisplayName(),
                List.copyOf(capabilities));
    }
}
