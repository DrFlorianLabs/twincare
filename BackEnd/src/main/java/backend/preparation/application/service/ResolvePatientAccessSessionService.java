package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.ResolvePatientAccessSessionCommand;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.domain.SecureToken;
import backend.preparation.domain.TokenDigest;

public final class ResolvePatientAccessSessionService implements ResolvePatientAccessSessionUseCase {

    private static final SecureToken DUMMY_TOKEN = new SecureToken("A".repeat(SecureToken.ENCODED_LENGTH));

    private final PatientAccessSessionRepository sessions;
    private final SecureTokenDigester tokenDigester;
    private final Clock clock;

    public ResolvePatientAccessSessionService(
            PatientAccessSessionRepository sessions,
            SecureTokenDigester tokenDigester,
            Clock clock) {
        this.sessions = Objects.requireNonNull(sessions);
        this.tokenDigester = Objects.requireNonNull(tokenDigester);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PatientAccessGrant resolve(ResolvePatientAccessSessionCommand command) {
        ParsedClientToken parsedToken = digestClientToken(command == null ? null : command.sessionToken());
        var matchedScope = sessions.findScopeByTokenDigest(parsedToken.digest());
        if (parsedToken.malformed()) {
            throw new PatientSessionUnavailableException();
        }
        Instant now = clock.instant();
        var scope = matchedScope
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(PatientSessionUnavailableException::new);
        return new PatientAccessGrant(
                scope.sessionId(),
                scope.invitationId(),
                scope.organizationId(),
                scope.preparationCaseId(),
                scope.expiresAt());
    }

    private ParsedClientToken digestClientToken(String rawToken) {
        try {
            return new ParsedClientToken(
                    tokenDigester.digestSession(new SecureToken(rawToken)), false);
        } catch (IllegalArgumentException exception) {
            return new ParsedClientToken(tokenDigester.digestSession(DUMMY_TOKEN), true);
        }
    }

    private record ParsedClientToken(TokenDigest digest, boolean malformed) {
    }
}
