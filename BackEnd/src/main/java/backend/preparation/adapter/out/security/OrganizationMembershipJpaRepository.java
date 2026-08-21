package backend.preparation.adapter.out.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationMembershipJpaRepository extends JpaRepository<OrganizationMembershipJpaEntity, UUID> {

    boolean existsByOrganizationIdAndUserIdAndActiveTrueAndMembershipRoleIn(
            String organizationId,
            Long userId,
            Collection<OrganizationMembershipRole> membershipRoles);
}
