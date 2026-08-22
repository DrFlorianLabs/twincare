package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;

public record PatientReasonLookup(
        UUID sessionId,
        PatientInvitationId invitationId,
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        Instant sessionExpiresAt,
        Instant readAt) {

    public PatientReasonLookup {
        Objects.requireNonNull(sessionId, "Patient access session identifier is required");
        Objects.requireNonNull(invitationId, "Patient invitation identifier is required");
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(sessionExpiresAt, "Patient access session expiry is required");
        Objects.requireNonNull(readAt, "Patient reason read timestamp is required");
    }

    @Override
    public String toString() {
        return "PatientReasonLookup[technical scope redacted]";
    }
}
