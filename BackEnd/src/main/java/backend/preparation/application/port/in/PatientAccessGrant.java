package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;

public record PatientAccessGrant(
        UUID sessionId,
        PatientInvitationId invitationId,
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        Instant expiresAt) {

    public PatientAccessGrant {
        Objects.requireNonNull(sessionId, "Patient access session identifier is required");
        Objects.requireNonNull(invitationId, "Patient invitation identifier is required");
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(expiresAt, "Patient access session expiry is required");
    }

    @Override
    public String toString() {
        return "PatientAccessGrant[technical scope redacted, expiresAt=" + expiresAt + "]";
    }
}
