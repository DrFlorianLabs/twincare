package backend.preparation.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.dao.AppUtilisateurRepository;
import backend.exception.UnauthorizedActionException;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.domain.OrganizationId;
import backend.service.SecurityHelperService;

class JpaPreparationCaseAuthorizationTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final long USER_ID = 42L;

    private SecurityHelperService securityHelperService;
    private OrganizationMembershipJpaRepository membershipRepository;
    private AppUtilisateur currentUser;
    private AppUtilisateurRepository userRepository;
    private JpaPreparationCaseAuthorization authorization;

    @BeforeEach
    void setUp() {
        securityHelperService = mock(SecurityHelperService.class);
        membershipRepository = mock(OrganizationMembershipJpaRepository.class);
        currentUser = mock(AppUtilisateur.class);
        userRepository = mock(AppUtilisateurRepository.class);
        when(currentUser.getId()).thenReturn(USER_ID);
        when(securityHelperService.getCurrentUser()).thenReturn(currentUser);
        authorization = new JpaPreparationCaseAuthorization(
                securityHelperService, membershipRepository, userRepository);
    }

    @Test
    void clinicianCanUseEveryDeclaredClinicalCapabilityWithAnActiveClinicianMembership() {
        when(currentUser.getRole()).thenReturn(Role.MEDECIN);
        activeMembership(OrganizationMembershipRole.CLINICIAN);

        for (PreparationCaseCapability capability : PreparationCaseCapability.values()) {
            assertThat(authorization.requireAccess(ORGANIZATION_ID, capability)).isEqualTo(USER_ID);
        }
    }

    @Test
    void medicalAssistantCannotConfirmReadyCloseOrReadAudit() {
        when(currentUser.getRole()).thenReturn(Role.ASSISTANT_MEDICAL);
        activeMembership(OrganizationMembershipRole.MEDICAL_ASSISTANT);

        assertThat(authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.CREATE_PREPARATION_CASE)).isEqualTo(USER_ID);
        assertThat(authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.ISSUE_PATIENT_INVITATION)).isEqualTo(USER_ID);
        assertThat(authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.VIEW_PREPARATION)).isEqualTo(USER_ID);
        assertThat(authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.MANAGE_PREPARATION_TASKS)).isEqualTo(USER_ID);
        assertThat(authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.DOWNLOAD_ATTACHMENT)).isEqualTo(USER_ID);
        assertThatThrownBy(() -> authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.CONFIRM_READY_FOR_CONSULTATION))
                .isInstanceOf(UnauthorizedActionException.class);
        assertThatThrownBy(() -> authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.CLOSE_PREPARATION_CASE))
                .isInstanceOf(UnauthorizedActionException.class);
        assertThatThrownBy(() -> authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.VIEW_PREPARATION_AUDIT))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    void platformAdministratorNeverReceivesClinicalAccessByDefault() {
        when(currentUser.getRole()).thenReturn(Role.ADMIN);

        for (PreparationCaseCapability capability : PreparationCaseCapability.values()) {
            assertThatThrownBy(() -> authorization.requireAccess(ORGANIZATION_ID, capability))
                    .isInstanceOf(UnauthorizedActionException.class);
        }
        verify(membershipRepository, never())
                .findByOrganizationIdAndUserIdAndActiveTrue(ORGANIZATION_ID.value(), USER_ID);
    }

    @Test
    void viewMembershipRolesExcludeOrganizationAdministrators() {
        when(currentUser.getRole()).thenReturn(Role.MEDECIN);
        activeMembership(OrganizationMembershipRole.ORGANIZATION_ADMIN);

        assertThatThrownBy(() -> authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.VIEW_PREPARATION))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(membershipRepository)
                .findByOrganizationIdAndUserIdAndActiveTrue(ORGANIZATION_ID.value(), USER_ID);
    }

    @Test
    void assignmentRequiresAnActiveClinicalStaffIdentityInTheSameOrganization() {
        AppUtilisateur assignee = mock(AppUtilisateur.class);
        when(assignee.getRole()).thenReturn(Role.ASSISTANT_MEDICAL);
        when(userRepository.findById(84L)).thenReturn(Optional.of(assignee));
        when(membershipRepository.existsByOrganizationIdAndUserIdAndActiveTrueAndMembershipRoleIn(
                eq(ORGANIZATION_ID.value()), eq(84L), anyCollection()))
                .thenReturn(true);

        authorization.requireAssignableStaff(ORGANIZATION_ID, 84L);

        verify(membershipRepository).existsByOrganizationIdAndUserIdAndActiveTrueAndMembershipRoleIn(
                eq(ORGANIZATION_ID.value()), eq(84L), anyCollection());
    }

    @Test
    void assignmentRejectsAPlatformPatientEvenWithCorruptClinicalMembership() {
        AppUtilisateur assignee = mock(AppUtilisateur.class);
        when(assignee.getRole()).thenReturn(Role.PATIENT);
        when(userRepository.findById(84L)).thenReturn(Optional.of(assignee));

        assertThatThrownBy(() -> authorization.requireAssignableStaff(ORGANIZATION_ID, 84L))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(membershipRepository, never())
                .existsByOrganizationIdAndUserIdAndActiveTrueAndMembershipRoleIn(
                        eq(ORGANIZATION_ID.value()), eq(84L), anyCollection());
    }

    private void activeMembership(OrganizationMembershipRole role) {
        OrganizationMembershipJpaEntity membership = mock(OrganizationMembershipJpaEntity.class);
        when(membership.membershipRole()).thenReturn(role);
        when(membershipRepository.findByOrganizationIdAndUserIdAndActiveTrue(
                ORGANIZATION_ID.value(), USER_ID)).thenReturn(Optional.of(membership));
    }
}
