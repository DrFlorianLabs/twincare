package backend.clinicalcontext.adapter.out.security;

import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import backend.clinicalcontext.application.port.out.AuthorizedClinicalReviewer;
import backend.clinicalcontext.application.port.out.ClinicalFactReviewAuthorization;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalReviewerReference;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.exception.UnauthorizedActionException;
import backend.service.SecurityHelperService;

@Component
public final class DatabaseClinicalFactReviewAuthorization
        implements ClinicalFactReviewAuthorization {

    private static final Set<String> CLINICAL_MEMBERSHIP_ROLES = Set.of(
            "CLINICIAN", "MEDICAL_ASSISTANT");

    private final SecurityHelperService securityHelperService;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseClinicalFactReviewAuthorization(
            SecurityHelperService securityHelperService,
            JdbcTemplate jdbcTemplate) {
        this.securityHelperService = securityHelperService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AuthorizedClinicalReviewer requireClinicalReviewerAccess(ClinicalContextOwner owner) {
        AppUtilisateur currentUser = securityHelperService.getCurrentUser();
        if (!isClinicalStaff(currentUser.getRole())) {
            throw new UnauthorizedActionException("Accès clinique interdit");
        }
        String membershipRole = jdbcTemplate.query(
                        """
                        SELECT membership_role
                        FROM organization_membership
                        WHERE organization_id = ? AND user_id = ? AND active = TRUE
                        """,
                        resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                        owner.value(),
                        currentUser.getId());
        if (membershipRole == null || !CLINICAL_MEMBERSHIP_ROLES.contains(membershipRole)) {
            throw new UnauthorizedActionException("Accès clinique interdit");
        }
        return new AuthorizedClinicalReviewer(
                new ClinicalReviewerReference("local-user:" + currentUser.getId()),
                currentUser.getId());
    }

    private boolean isClinicalStaff(Role role) {
        return role == Role.MEDECIN || role == Role.ASSISTANT_MEDICAL;
    }
}
