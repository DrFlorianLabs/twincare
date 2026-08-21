package backend.preparation.adapter.out.security;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrganizationMembershipJpaRepository extends JpaRepository<OrganizationMembershipJpaEntity, UUID> {

    boolean existsByOrganizationIdAndUserIdAndActiveTrueAndMembershipRoleIn(
            String organizationId,
            Long userId,
            Collection<OrganizationMembershipRole> membershipRoles);

    Optional<OrganizationMembershipJpaEntity> findByOrganizationIdAndUserIdAndActiveTrue(
            String organizationId,
            Long userId);

    @Query(value = """
            SELECT membership.organization_id AS organizationId,
                   organization.display_name AS displayName,
                   membership.membership_role AS membershipRole
            FROM organization_membership membership
            JOIN care_organization organization ON organization.id = membership.organization_id
            WHERE membership.user_id = :userId
              AND membership.active = TRUE
            ORDER BY organization.display_name, membership.organization_id
            """, nativeQuery = true)
    List<ActiveOrganizationMembershipProjection> findActiveOrganizationsForUser(
            @Param("userId") Long userId);
}
