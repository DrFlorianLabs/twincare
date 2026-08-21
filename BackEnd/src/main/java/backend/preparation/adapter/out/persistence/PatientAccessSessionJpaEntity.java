package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PatientAccessSession;
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

    void revoke(Instant occurredAt) {
        if (revokedAt == null) {
            revokedAt = occurredAt;
        }
    }
}
