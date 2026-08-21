package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;

public record PatientAccessSessionScope(
        UUID sessionId,
        PatientInvitationId invitationId,
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        String scope,
        PatientInvitationStatus invitationStatus,
        PreparationCaseStatus preparationCaseStatus,
        Instant issuedAt,
        Instant expiresAt,
        Optional<Instant> revokedAt) {

    public static final String CONTRIBUTION_SCOPE = "PREPARATION_CASE_CONTRIBUTE";

    public PatientAccessSessionScope {
        Objects.requireNonNull(sessionId, "Patient access session identifier is required");
        Objects.requireNonNull(invitationId, "Patient invitation identifier is required");
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(scope, "Patient access scope is required");
        Objects.requireNonNull(invitationStatus, "Patient invitation status is required");
        Objects.requireNonNull(preparationCaseStatus, "Preparation case status is required");
        Objects.requireNonNull(issuedAt, "Patient access session issue timestamp is required");
        Objects.requireNonNull(expiresAt, "Patient access session expiry is required");
        Objects.requireNonNull(revokedAt, "Patient access session revocation container is required");
    }

    public boolean isUsableAt(Instant instant) {
        Objects.requireNonNull(instant, "Session validation timestamp is required");
        return CONTRIBUTION_SCOPE.equals(scope)
                && invitationStatus == PatientInvitationStatus.CONSUMED
                && preparationCaseStatus.allowsPatientContribution()
                && revokedAt.isEmpty()
                && !instant.isBefore(issuedAt)
                && instant.isBefore(expiresAt);
    }
}
