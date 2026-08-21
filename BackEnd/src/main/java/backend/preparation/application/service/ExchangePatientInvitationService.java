package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import backend.preparation.application.PatientInvitationUnavailableException;
import backend.preparation.application.port.in.ExchangePatientInvitationCommand;
import backend.preparation.application.port.in.ExchangePatientInvitationResult;
import backend.preparation.application.port.in.ExchangePatientInvitationUseCase;
import backend.preparation.application.port.out.PatientAccessSessionIdGenerator;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationExchangeGuard;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.application.port.out.SecureTokenGenerator;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.SecureToken;
import backend.preparation.domain.TokenDigest;

public final class ExchangePatientInvitationService implements ExchangePatientInvitationUseCase {

    private static final SecureToken DUMMY_TOKEN = new SecureToken("A".repeat(SecureToken.ENCODED_LENGTH));

    private final PatientInvitationRepository invitations;
    private final PreparationCaseRepository preparationCases;
    private final PatientAccessSessionRepository accessSessions;
    private final PatientAccessSessionIdGenerator sessionIdGenerator;
    private final SecureTokenGenerator tokenGenerator;
    private final SecureTokenDigester tokenDigester;
    private final PatientInvitationExchangeGuard exchangeGuard;
    private final Clock clock;

    public ExchangePatientInvitationService(
            PatientInvitationRepository invitations,
            PreparationCaseRepository preparationCases,
            PatientAccessSessionRepository accessSessions,
            PatientAccessSessionIdGenerator sessionIdGenerator,
            SecureTokenGenerator tokenGenerator,
            SecureTokenDigester tokenDigester,
            PatientInvitationExchangeGuard exchangeGuard,
            Clock clock) {
        this.invitations = Objects.requireNonNull(invitations);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.accessSessions = Objects.requireNonNull(accessSessions);
        this.sessionIdGenerator = Objects.requireNonNull(sessionIdGenerator);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.tokenDigester = Objects.requireNonNull(tokenDigester);
        this.exchangeGuard = Objects.requireNonNull(exchangeGuard);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ExchangePatientInvitationResult exchange(ExchangePatientInvitationCommand command) {
        Instant now = clock.instant();
        exchangeGuard.requireAttemptAllowed(now);
        ParsedClientToken parsedToken = digestClientToken(command == null ? null : command.token());
        var preliminaryMatch = invitations.findScopeByTokenDigest(parsedToken.digest());
        if (parsedToken.malformed()) {
            throw new PatientInvitationUnavailableException();
        }
        var preliminaryScope = preliminaryMatch
                .orElseThrow(PatientInvitationUnavailableException::new);
        PreparationCase preparationCase = preparationCases
                .findByIdForUpdate(
                        preliminaryScope.preparationCaseId(),
                        preliminaryScope.organizationId())
                .orElseThrow(PatientInvitationUnavailableException::new);
        PatientInvitation invitation = invitations.findByTokenDigestForUpdate(parsedToken.digest())
                .orElseThrow(PatientInvitationUnavailableException::new);
        if (!invitation.id().equals(preliminaryScope.invitationId())
                || !invitation.preparationCaseId().equals(preparationCase.id())
                || !invitation.organizationId().equals(preparationCase.organizationId())) {
            throw new PatientInvitationUnavailableException();
        }
        if (invitation.status() != PatientInvitationStatus.ACTIVE) {
            throw new PatientInvitationUnavailableException();
        }
        if (invitation.expiresAtOrBefore(now)) {
            invitation.expire(now);
            invitations.update(invitation);
            throw new PatientInvitationUnavailableException();
        }

        if (preparationCase.status() != PreparationCaseStatus.CREATED) {
            invitation.revoke(now);
            invitations.update(invitation);
            accessSessions.revokeByInvitationId(invitation.id(), now);
            throw new PatientInvitationUnavailableException();
        }

        SecureToken sessionToken = tokenGenerator.nextToken();
        PatientAccessSession session = PatientAccessSession.issue(
                sessionIdGenerator.nextId(),
                invitation,
                tokenDigester.digestSession(sessionToken),
                now);
        invitation.consume(now);
        preparationCase.startPatientPreparation(now);
        accessSessions.add(session);
        invitations.update(invitation);
        preparationCases.update(preparationCase);
        return new ExchangePatientInvitationResult(sessionToken, session.issuedAt(), session.expiresAt());
    }

    private ParsedClientToken digestClientToken(String rawToken) {
        try {
            return new ParsedClientToken(
                    tokenDigester.digestInvitation(new SecureToken(rawToken)),
                    false);
        } catch (IllegalArgumentException exception) {
            return new ParsedClientToken(tokenDigester.digestInvitation(DUMMY_TOKEN), true);
        }
    }

    private record ParsedClientToken(TokenDigest digest, boolean malformed) {
    }
}
