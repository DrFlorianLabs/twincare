package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientAccessSessionScope;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationAttachment;
import backend.preparation.domain.PreparationCase;

public final class PatientAttachmentScopeLock {

    private final PreparationCaseRepository preparationCases;
    private final PatientInvitationRepository invitations;
    private final PatientAccessSessionRepository sessions;
    private final PreparationAttachmentRepository attachments;
    private final Clock clock;

    public PatientAttachmentScopeLock(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository sessions,
            PreparationAttachmentRepository attachments,
            Clock clock) {
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.invitations = Objects.requireNonNull(invitations);
        this.sessions = Objects.requireNonNull(sessions);
        this.attachments = Objects.requireNonNull(attachments);
        this.clock = Objects.requireNonNull(clock);
    }

    LockedPatientAttachmentScope lock(PatientAccessGrant grant) {
        if (grant == null) {
            throw new PatientSessionUnavailableException();
        }
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
        Optional<PreparationAttachment> attachment = attachments
                .findByCaseForUpdate(preparationCase.id(), preparationCase.organizationId());
        Instant now = clock.instant();

        if (!session.preparationCaseId().equals(preparationCase.id())
                || !session.organizationId().equals(preparationCase.organizationId())
                || invitation.status() != PatientInvitationStatus.CONSUMED
                || !session.expiresAt().equals(grant.expiresAt())
                || !now.isBefore(grant.expiresAt())
                || !preparationCase.status().allowsPatientContribution()
                || invitation.expiresAtOrBefore(now)
                || !session.isActiveAt(now)) {
            throw new PatientSessionUnavailableException();
        }
        return new LockedPatientAttachmentScope(preparationCase, attachment, now);
    }

    long touchVisibleContent(LockedPatientAttachmentScope lockedScope) {
        lockedScope.preparationCase().touchVisibleContent(lockedScope.now());
        return preparationCases.update(lockedScope.preparationCase());
    }

    record LockedPatientAttachmentScope(
            PreparationCase preparationCase,
            Optional<PreparationAttachment> attachment,
            Instant now) {
    }
}
