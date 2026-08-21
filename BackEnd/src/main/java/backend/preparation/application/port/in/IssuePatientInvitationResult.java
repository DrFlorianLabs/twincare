package backend.preparation.application.port.in;

import java.time.Instant;

import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.SecureToken;

public record IssuePatientInvitationResult(
        PatientInvitationId invitationId,
        SecureToken token,
        Instant expiresAt) {
}
