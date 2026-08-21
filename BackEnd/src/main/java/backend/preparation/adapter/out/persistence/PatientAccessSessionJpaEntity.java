package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.TokenDigest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "patient_access_session")
class PatientAccessSessionJpaEntity {

    private static final String SCOPE = "PREPARATION_CASE_CONTRIBUTE";

    @Id
    private UUID id;

    @Column(name = "invitation_id", nullable = false, unique = true)
    private UUID invitationId;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "preparation_case_id", nullable = false)
    private UUID preparationCaseId;

    @Column(nullable = false, length = 48)
    private String scope;

    @Column(name = "token_key_id", nullable = false, length = 64)
    private String tokenKeyId;

    @Column(name = "token_digest", nullable = false, length = 64)
    private String tokenDigest;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PatientAccessSessionJpaEntity() {
    }

    PatientAccessSessionJpaEntity(PatientAccessSession session) {
        id = session.id();
        invitationId = session.invitationId().value();
        organizationId = session.organizationId().value();
        preparationCaseId = session.preparationCaseId().value();
        scope = SCOPE;
        tokenKeyId = session.tokenDigest().keyId();
        tokenDigest = session.tokenDigest().value();
        issuedAt = session.issuedAt();
        expiresAt = session.expiresAt();
        revokedAt = session.revokedAt().orElse(null);
    }

    UUID invitationId() {
        return invitationId;
    }

    PatientAccessSession toDomain() {
        return PatientAccessSession.restore(
                id,
                new PatientInvitationId(invitationId),
                new OrganizationId(organizationId),
                new PreparationCaseId(preparationCaseId),
                new TokenDigest(tokenKeyId, tokenDigest),
                issuedAt,
                expiresAt,
                java.util.Optional.ofNullable(revokedAt));
    }

    void updateFrom(PatientAccessSession session) {
        if (!id.equals(session.id())
                || !invitationId.equals(session.invitationId().value())
                || !organizationId.equals(session.organizationId().value())
                || !preparationCaseId.equals(session.preparationCaseId().value())) {
            throw new IllegalArgumentException("Patient access session identity cannot change");
        }
        revokedAt = session.revokedAt().orElse(null);
    }

    boolean revoke(Instant occurredAt) {
        if (revokedAt == null) {
            revokedAt = occurredAt;
            return true;
        }
        return false;
    }
}
