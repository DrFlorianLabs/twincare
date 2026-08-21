package backend.preparation.adapter.out.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_membership")
class OrganizationMembershipJpaEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private OrganizationMembershipRole membershipRole;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrganizationMembershipJpaEntity() {
    }

    OrganizationMembershipRole membershipRole() {
        return membershipRole;
    }
}
