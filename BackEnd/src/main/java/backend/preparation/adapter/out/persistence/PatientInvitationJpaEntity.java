package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "patient_invitation")
class PatientInvitationJpaEntity {

    private static final String PURPOSE = "PREPARATION_CASE_ACCESS";
    private static final String ACTIVE_SLOT = "ACTIVE";

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "preparation_case_id", nullable = false)
    private UUID preparationCaseId;

    @Column(nullable = false, length = 40)
    private String purpose;

    @Column(name = "token_key_id", nullable = false, length = 64)
    private String tokenKeyId;

    @Column(name = "token_digest", nullable = false, length = 64)
    private String tokenDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private PatientInvitationStatus status;

    @Column(name = "active_slot", length = 16)
    private String activeSlot;

    @Column(name = "issued_by_user_id", nullable = false)
    private Long issuedByUserId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PatientInvitationJpaEntity() {
    }

    PatientInvitationJpaEntity(PatientInvitation invitation) {
        id = invitation.id().value();
        organizationId = invitation.organizationId().value();
        preparationCaseId = invitation.preparationCaseId().value();
        purpose = PURPOSE;
        applyState(invitation);
        issuedByUserId = invitation.issuedByUserId();
        issuedAt = invitation.issuedAt();
        expiresAt = invitation.expiresAt();
    }

    void updateFrom(PatientInvitation invitation) {
        if (!id.equals(invitation.id().value())
                || !organizationId.equals(invitation.organizationId().value())
                || !preparationCaseId.equals(invitation.preparationCaseId().value())) {
            throw new IllegalArgumentException("Patient invitation identity cannot change");
        }
        applyState(invitation);
    }

    private void applyState(PatientInvitation invitation) {
        tokenKeyId = invitation.tokenDigest().keyId();
        tokenDigest = invitation.tokenDigest().value();
        status = invitation.status();
        activeSlot = status == PatientInvitationStatus.ACTIVE ? ACTIVE_SLOT : null;
        consumedAt = invitation.consumedAt().orElse(null);
        revokedAt = invitation.revokedAt().orElse(null);
        updatedAt = invitation.updatedAt();
    }

    UUID id() {
        return id;
    }

    String organizationId() {
        return organizationId;
    }

    UUID preparationCaseId() {
        return preparationCaseId;
    }

    String tokenKeyId() {
        return tokenKeyId;
    }

    String tokenDigest() {
        return tokenDigest;
    }

    PatientInvitationStatus status() {
        return status;
    }

    Long issuedByUserId() {
        return issuedByUserId;
    }

    Instant issuedAt() {
        return issuedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Instant consumedAt() {
        return consumedAt;
    }

    Instant revokedAt() {
        return revokedAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
