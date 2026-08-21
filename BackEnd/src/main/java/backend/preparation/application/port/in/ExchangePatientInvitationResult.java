package backend.preparation.application.port.in;

import java.time.Instant;

import backend.preparation.domain.SecureToken;

public record ExchangePatientInvitationResult(
        SecureToken sessionToken,
        Instant issuedAt,
        Instant expiresAt) {
}
