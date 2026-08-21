package backend.preparation.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import backend.preparation.application.PatientInvitationConflictException;
import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.IssuePatientInvitationCommand;
import backend.preparation.application.port.in.IssuePatientInvitationResult;
import backend.preparation.application.port.in.IssuePatientInvitationUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationIdGenerator;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.application.port.out.SecureTokenGenerator;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.SecureToken;

public final class IssuePatientInvitationService implements IssuePatientInvitationUseCase {

    private static final Duration MAXIMUM_TTL = Duration.ofDays(7);

    private final PreparationCaseAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository accessSessions;
    private final PatientInvitationIdGenerator invitationIdGenerator;
    private final SecureTokenGenerator tokenGenerator;
    private final SecureTokenDigester tokenDigester;
    private final Clock clock;
    private final Duration invitationTtl;

    public IssuePatientInvitationService(
            PreparationCaseAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PatientInvitationIdGenerator invitationIdGenerator,
            SecureTokenGenerator tokenGenerator,
            SecureTokenDigester tokenDigester,
            Clock clock,
            Duration invitationTtl) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.invitations = Objects.requireNonNull(invitations);
        this.accessSessions = Objects.requireNonNull(accessSessions);
        this.invitationIdGenerator = Objects.requireNonNull(invitationIdGenerator);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.tokenDigester = Objects.requireNonNull(tokenDigester);
        this.clock = Objects.requireNonNull(clock);
        this.invitationTtl = validateTtl(invitationTtl);
    }

    @Override
    public IssuePatientInvitationResult issue(IssuePatientInvitationCommand command) {
        Objects.requireNonNull(command, "Patient invitation command is required");
        long actorId = authorization.requireStaffAccess(command.organizationId());
        PreparationCase preparationCase = preparationCases
                .findByIdForUpdate(command.preparationCaseId(), command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);
        if (preparationCase.status() != PreparationCaseStatus.CREATED) {
            throw new PatientInvitationConflictException();
        }

        Instant now = clock.instant();
        Instant expiresAt = earliest(now.plus(invitationTtl), preparationCase.scheduledAt().orElse(null));
        if (!expiresAt.isAfter(now)) {
            throw new PatientInvitationConflictException();
        }

        invitations.findActiveForUpdate(preparationCase.id(), preparationCase.organizationId())
                .ifPresent(previous -> {
                    previous.revoke(now);
                    invitations.update(previous);
                    accessSessions.revokeByInvitationId(previous.id(), now);
                });

        SecureToken token = tokenGenerator.nextToken();
        PatientInvitation invitation = PatientInvitation.issue(
                invitationIdGenerator.nextId(),
                preparationCase.organizationId(),
                preparationCase.id(),
                actorId,
                tokenDigester.digestInvitation(token),
                now,
                expiresAt);
        invitations.add(invitation);
        return new IssuePatientInvitationResult(invitation.id(), token, invitation.expiresAt());
    }

    private Duration validateTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "Patient invitation time-to-live is required");
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAXIMUM_TTL) > 0) {
            throw new IllegalArgumentException("Patient invitation time-to-live must be between zero and seven days");
        }
        return ttl;
    }

    private Instant earliest(Instant first, Instant second) {
        return second == null || first.isBefore(second) ? first : second;
    }
}
