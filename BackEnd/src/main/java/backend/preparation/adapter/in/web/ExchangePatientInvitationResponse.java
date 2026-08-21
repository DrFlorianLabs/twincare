package backend.preparation.adapter.in.web;

import java.time.Instant;

public record ExchangePatientInvitationResponse(
        String status,
        Instant expiresAt) {
}
