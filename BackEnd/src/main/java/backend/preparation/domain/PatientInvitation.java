package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PatientInvitation {

    private final PatientInvitationId id;
    private final OrganizationId organizationId;
    private final PreparationCaseId preparationCaseId;
    private final long issuedByUserId;
    private final TokenDigest tokenDigest;
    private final Instant issuedAt;
    private final Instant expiresAt;

    private PatientInvitationStatus status;
    private Optional<Instant> consumedAt;
    private Optional<Instant> revokedAt;
    private Instant updatedAt;

    private PatientInvitation(
            PatientInvitationId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            long issuedByUserId,
            TokenDigest tokenDigest,
            PatientInvitationStatus status,
            Instant issuedAt,
            Instant expiresAt,
            Optional<Instant> consumedAt,
            Optional<Instant> revokedAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Patient invitation identifier is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization identifier is required");
        this.preparationCaseId = Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        if (issuedByUserId <= 0) {
            throw new IllegalArgumentException("Invitation issuer identifier is invalid");
        }
        this.issuedByUserId = issuedByUserId;
        this.tokenDigest = Objects.requireNonNull(tokenDigest, "Invitation token digest is required");
        this.status = Objects.requireNonNull(status, "Patient invitation status is required");
        this.issuedAt = Objects.requireNonNull(issuedAt, "Invitation issue timestamp is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Invitation expiry timestamp is required");
        this.consumedAt = Objects.requireNonNull(consumedAt, "Invitation consumption timestamp container is required");
        this.revokedAt = Objects.requireNonNull(revokedAt, "Invitation revocation timestamp container is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Invitation update timestamp is required");
        validateState();
    }

    public static PatientInvitation issue(
            PatientInvitationId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            long issuedByUserId,
            TokenDigest tokenDigest,
            Instant issuedAt,
            Instant expiresAt) {
        return new PatientInvitation(
                id,
                organizationId,
                preparationCaseId,
                issuedByUserId,
                tokenDigest,
                PatientInvitationStatus.ACTIVE,
                issuedAt,
                expiresAt,
                Optional.empty(),
                Optional.empty(),
                issuedAt);
    }

    public static PatientInvitation restore(
            PatientInvitationId id,
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            long issuedByUserId,
            TokenDigest tokenDigest,
            PatientInvitationStatus status,
            Instant issuedAt,
            Instant expiresAt,
            Optional<Instant> consumedAt,
            Optional<Instant> revokedAt,
            Instant updatedAt) {
        return new PatientInvitation(
                id,
                organizationId,
                preparationCaseId,
                issuedByUserId,
                tokenDigest,
                status,
                issuedAt,
                expiresAt,
                consumedAt,
                revokedAt,
                updatedAt);
    }

    private void validateState() {
        if (!expiresAt.isAfter(issuedAt) || updatedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Patient invitation timeline is invalid");
        }
        if (consumedAt.filter(timestamp -> timestamp.isBefore(issuedAt) || timestamp.isAfter(updatedAt)).isPresent()
                || revokedAt.filter(timestamp -> timestamp.isBefore(issuedAt) || timestamp.isAfter(updatedAt)).isPresent()) {
            throw new IllegalArgumentException("Patient invitation event timestamp is invalid");
        }
        if (status == PatientInvitationStatus.CONSUMED && consumedAt.isEmpty()) {
            throw new IllegalArgumentException("Patient invitation consumption state is inconsistent");
        }
        if (status != PatientInvitationStatus.CONSUMED
                && status != PatientInvitationStatus.REVOKED
                && consumedAt.isPresent()) {
            throw new IllegalArgumentException("Patient invitation consumption state is inconsistent");
        }
        if ((status == PatientInvitationStatus.REVOKED) != revokedAt.isPresent()) {
            throw new IllegalArgumentException("Patient invitation revocation state is inconsistent");
        }
        if ((status == PatientInvitationStatus.ACTIVE || status == PatientInvitationStatus.EXPIRED)
                && (consumedAt.isPresent() || revokedAt.isPresent())) {
            throw new IllegalArgumentException("Patient invitation terminal timestamps are inconsistent");
        }
    }

    public boolean expiresAtOrBefore(Instant instant) {
        return !Objects.requireNonNull(instant, "Comparison timestamp is required").isBefore(expiresAt);
    }

    public void consume(Instant occurredAt) {
        requireActive(occurredAt);
        status = PatientInvitationStatus.CONSUMED;
        consumedAt = Optional.of(occurredAt);
        updatedAt = occurredAt;
    }

    public void expire(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Expiration timestamp is required");
        if (status != PatientInvitationStatus.ACTIVE || occurredAt.isBefore(expiresAt)) {
            throw new IllegalStateException("Patient invitation cannot be expired");
        }
        status = PatientInvitationStatus.EXPIRED;
        updatedAt = occurredAt;
    }

    public void revoke(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Revocation timestamp is required");
        if (occurredAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Revocation timestamp cannot precede the previous invitation event");
        }
        if (status == PatientInvitationStatus.REVOKED) {
            return;
        }
        status = PatientInvitationStatus.REVOKED;
        revokedAt = Optional.of(occurredAt);
        updatedAt = occurredAt;
    }

    private void requireActive(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Consumption timestamp is required");
        if (status != PatientInvitationStatus.ACTIVE
                || occurredAt.isBefore(updatedAt)
                || expiresAtOrBefore(occurredAt)) {
            throw new IllegalStateException("Patient invitation is unavailable");
        }
    }

    public PatientInvitationId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public PreparationCaseId preparationCaseId() {
        return preparationCaseId;
    }

    public long issuedByUserId() {
        return issuedByUserId;
    }

    public TokenDigest tokenDigest() {
        return tokenDigest;
    }

    public PatientInvitationStatus status() {
        return status;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> consumedAt() {
        return consumedAt;
    }

    public Optional<Instant> revokedAt() {
        return revokedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
