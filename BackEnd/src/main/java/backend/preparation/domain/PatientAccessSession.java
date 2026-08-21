package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PatientAccessSession {

    private final UUID id;
    private final PatientInvitationId invitationId;
    private final OrganizationId organizationId;
    private final PreparationCaseId preparationCaseId;
    private final TokenDigest tokenDigest;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Optional<Instant> revokedAt;

    private PatientAccessSession(
            UUID id,
            PatientInvitationId invitationId,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            TokenDigest tokenDigest,
            Instant issuedAt,
            Instant expiresAt,
            Optional<Instant> revokedAt) {
        this.id = Objects.requireNonNull(id, "Patient access session identifier is required");
        this.invitationId = Objects.requireNonNull(invitationId, "Patient invitation identifier is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization identifier is required");
        this.preparationCaseId = Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        this.tokenDigest = Objects.requireNonNull(tokenDigest, "Patient access session token digest is required");
        this.issuedAt = Objects.requireNonNull(issuedAt, "Patient access session issue timestamp is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Patient access session expiry timestamp is required");
        this.revokedAt = Objects.requireNonNull(revokedAt, "Patient access session revocation container is required");
        if (!expiresAt.isAfter(issuedAt)
                || revokedAt.filter(timestamp -> timestamp.isBefore(issuedAt)).isPresent()) {
            throw new IllegalArgumentException("Patient access session timeline is invalid");
        }
    }

    public static PatientAccessSession issue(
            UUID id,
            PatientInvitation invitation,
            TokenDigest tokenDigest,
            Instant issuedAt) {
        return new PatientAccessSession(
                id,
                invitation.id(),
                invitation.organizationId(),
                invitation.preparationCaseId(),
                tokenDigest,
                issuedAt,
                invitation.expiresAt(),
                Optional.empty());
    }

    public static PatientAccessSession restore(
            UUID id,
            PatientInvitationId invitationId,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            TokenDigest tokenDigest,
            Instant issuedAt,
            Instant expiresAt,
            Optional<Instant> revokedAt) {
        return new PatientAccessSession(
                id, invitationId, organizationId, preparationCaseId, tokenDigest, issuedAt, expiresAt, revokedAt);
    }

    public void revoke(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Revocation timestamp is required");
        if (occurredAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Revocation timestamp cannot precede session issue");
        }
        revokedAt = Optional.of(occurredAt);
    }

    public UUID id() {
        return id;
    }

    public PatientInvitationId invitationId() {
        return invitationId;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public PreparationCaseId preparationCaseId() {
        return preparationCaseId;
    }

    public TokenDigest tokenDigest() {
        return tokenDigest;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> revokedAt() {
        return revokedAt;
    }
}
