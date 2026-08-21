package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record IssuePatientInvitationResponse(
        UUID invitationId,
        String invitationToken,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuePatientInvitationResponse[invitationId=" + invitationId
                + ", invitationToken=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
