package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.RecordPatientReasonCommand;
import backend.preparation.application.port.in.RecordPatientReasonUseCase;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientAccessSessionScope;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationCase;

public final class RecordPatientReasonService implements RecordPatientReasonUseCase {

    private final PreparationCaseRepository preparationCases;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository sessions;
    private final Clock clock;

    public RecordPatientReasonService(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository sessions,
            Clock clock) {
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.invitations = Objects.requireNonNull(invitations);
        this.sessions = Objects.requireNonNull(sessions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void record(RecordPatientReasonCommand command) {
        PatientAccessGrant grant = requireGrant(command == null ? null : command.grant());
        LockedPatientScope lockedScope = requireActiveScopeForUpdate(grant);
        Instant now = clock.instant();
        requireActiveAt(grant, lockedScope, now);
        if (lockedScope.preparationCase().recordPatientReason(command.reason(), now)) {
            preparationCases.update(lockedScope.preparationCase());
        }
    }

    private LockedPatientScope requireActiveScopeForUpdate(PatientAccessGrant grant) {
        sessions.findScopeByIdentity(
                        grant.sessionId(),
                        grant.invitationId(),
                        grant.preparationCaseId(),
                        grant.organizationId())
                .filter(scope -> PatientAccessSessionScope.CONTRIBUTION_SCOPE.equals(scope.scope()))
                .orElseThrow(PatientSessionUnavailableException::new);
        PreparationCase preparationCase = preparationCases
                .findByIdForUpdate(grant.preparationCaseId(), grant.organizationId())
                .orElseThrow(PatientSessionUnavailableException::new);
        PatientInvitation invitation = invitations
                .findByIdForUpdate(
                        grant.invitationId(), preparationCase.id(), preparationCase.organizationId())
                .orElseThrow(PatientSessionUnavailableException::new);
        PatientAccessSession session = sessions
                .findByIdForUpdate(
                        grant.sessionId(),
                        invitation.id(),
                        preparationCase.id(),
                        preparationCase.organizationId())
                .orElseThrow(PatientSessionUnavailableException::new);
        if (!session.preparationCaseId().equals(preparationCase.id())
                || !session.organizationId().equals(preparationCase.organizationId())
                || invitation.status() != PatientInvitationStatus.CONSUMED
                || !session.expiresAt().equals(grant.expiresAt())) {
            throw new PatientSessionUnavailableException();
        }
        return new LockedPatientScope(preparationCase, invitation, session);
    }

    private void requireActiveAt(
            PatientAccessGrant grant,
            LockedPatientScope lockedScope,
            Instant now) {
        if (!now.isBefore(grant.expiresAt())
                || !lockedScope.preparationCase().status().allowsPatientContribution()
                || lockedScope.invitation().expiresAtOrBefore(now)
                || !lockedScope.session().isActiveAt(now)) {
            throw new PatientSessionUnavailableException();
        }
    }

    private PatientAccessGrant requireGrant(PatientAccessGrant grant) {
        if (grant == null) {
            throw new PatientSessionUnavailableException();
        }
        return grant;
    }

    private record LockedPatientScope(
            PreparationCase preparationCase,
            PatientInvitation invitation,
            PatientAccessSession session) {
    }
}
