package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.CommitStagedPatientAttachmentCommand;
import backend.preparation.application.port.in.DeletePatientAttachmentCommand;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.out.AttachmentIdGenerator;
import backend.preparation.application.port.out.AttachmentStorageTransaction;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientAccessSessionScope;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.StagedAttachment;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.AttachmentDigest;
import backend.preparation.domain.AttachmentId;
import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.AttachmentStorageKey;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationAttachment;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.SecureToken;
import backend.preparation.domain.TokenDigest;

class PatientAttachmentServicesTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00.123456789Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final PatientInvitationId INVITATION_ID = new PatientInvitationId(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final UUID SESSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final TokenDigest DIGEST = new TokenDigest("test-v1", "d".repeat(64));

    private PreparationCaseRepository cases;
    private PatientInvitationRepository invitations;
    private PatientAccessSessionRepository sessions;
    private PreparationAttachmentRepository attachments;
    private AttachmentStorageTransaction storageTransaction;
    private PatientAccessGrant grant;
    private PreparationCase preparationCase;

    @BeforeEach
    void setUp() {
        cases = mock(PreparationCaseRepository.class);
        invitations = mock(PatientInvitationRepository.class);
        sessions = mock(PatientAccessSessionRepository.class);
        attachments = mock(PreparationAttachmentRepository.class);
        storageTransaction = mock(AttachmentStorageTransaction.class);
        grant = new PatientAccessGrant(SESSION_ID, INVITATION_ID, ORGANIZATION_ID, CASE_ID, NOW.plusSeconds(3600));
        preparationCase = preparationCase();

        when(sessions.findScopeByIdentity(SESSION_ID, INVITATION_ID, CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(scope()));
        when(cases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(invitations.findByIdForUpdate(INVITATION_ID, CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(invitation()));
        when(sessions.findByIdForUpdate(SESSION_ID, INVITATION_ID, CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(session()));
    }

    @Test
    void scopeIsLockedInGlobalOrderBeforeClockIsUsed() {
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        PatientAttachmentScopeLock lock = scopeLock();

        var result = lock.lock(grant);

        assertThat(result.now()).isEqualTo(NOW);
        var ordered = inOrder(sessions, cases, invitations, attachments);
        ordered.verify(sessions).findScopeByIdentity(SESSION_ID, INVITATION_ID, CASE_ID, ORGANIZATION_ID);
        ordered.verify(cases).findByIdForUpdate(CASE_ID, ORGANIZATION_ID);
        ordered.verify(invitations).findByIdForUpdate(INVITATION_ID, CASE_ID, ORGANIZATION_ID);
        ordered.verify(sessions).findByIdForUpdate(SESSION_ID, INVITATION_ID, CASE_ID, ORGANIZATION_ID);
        ordered.verify(attachments).findByCaseForUpdate(CASE_ID, ORGANIZATION_ID);
    }

    @Test
    void exactPutIsIdempotentWithoutStorageOrDatabaseMutation() {
        PreparationAttachment existing = existingAttachment();
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(existing));
        CommitStagedPatientAttachmentService service = commitService();

        var result = service.commit(new CommitStagedPatientAttachmentCommand(grant, staged("synthetic.pdf", "a")));

        assertThat(result.changed()).isFalse();
        verify(storageTransaction, never()).promoteForCommit(any());
        verify(storageTransaction, never()).deleteAfterCommit(any());
        verify(attachments, never()).update(any());
        verify(cases, never()).update(any());
    }

    @Test
    void newAttachmentUsesTheExplicitDatabaseTimestampPrecision() {
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());

        var result = commitService().commit(
                new CommitStagedPatientAttachmentCommand(grant, staged("synthetic.pdf", "a")));

        assertThat(result.uploadedAt()).isEqualTo(NOW.truncatedTo(ChronoUnit.MICROS));
        assertThat(result.updatedAt()).isEqualTo(NOW.truncatedTo(ChronoUnit.MICROS));
        verify(cases).update(preparationCase);
    }

    @Test
    void replacementPromotesNewBytesQuarantinesOldBytesAndUpdatesMetadata() {
        PreparationAttachment existing = existingAttachment();
        AttachmentStorageKey previousKey = existing.storageKey();
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(existing));
        CommitStagedPatientAttachmentService service = commitService();
        StagedAttachment replacement = staged("replacement.pdf", "b");

        var result = service.commit(new CommitStagedPatientAttachmentCommand(grant, replacement));

        assertThat(result.changed()).isTrue();
        assertThat(existing.storageKey()).isEqualTo(replacement.storageKey());
        var ordered = inOrder(storageTransaction, attachments);
        ordered.verify(storageTransaction).promoteForCommit(replacement);
        ordered.verify(storageTransaction).deleteAfterCommit(previousKey);
        ordered.verify(attachments).update(existing);
        verify(cases).update(preparationCase);
    }

    @Test
    void deletionTouchesTheCaseOnlyWhenVisibleContentWasRemoved() {
        PreparationAttachment existing = existingAttachment();
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(existing), Optional.empty());
        DeletePatientAttachmentService service = new DeletePatientAttachmentService(
                scopeLock(), attachments, storageTransaction, event -> { });

        service.delete(new DeletePatientAttachmentCommand(grant));
        service.delete(new DeletePatientAttachmentCommand(grant));

        verify(storageTransaction).deleteAfterCommit(existing.storageKey());
        verify(attachments).delete(existing);
        verify(cases).update(preparationCase);
    }

    @Test
    void revokedSessionFailsClosedBeforeAttachmentMutation() {
        PatientAccessSession revoked = session();
        revoked.revoke(NOW.minusSeconds(1));
        when(sessions.findByIdForUpdate(SESSION_ID, INVITATION_ID, CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revoked));
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commitService().commit(
                new CommitStagedPatientAttachmentCommand(grant, staged("synthetic.pdf", "a"))))
                .isInstanceOf(PatientSessionUnavailableException.class);
        verify(storageTransaction, never()).promoteForCommit(any());
        verify(attachments, never()).add(any());
    }

    private CommitStagedPatientAttachmentService commitService() {
        AttachmentIdGenerator idGenerator = () -> new AttachmentId(
                UUID.fromString("40000000-0000-0000-0000-000000000001"));
        return new CommitStagedPatientAttachmentService(
                scopeLock(), attachments, idGenerator, storageTransaction, event -> { });
    }

    private PatientAttachmentScopeLock scopeLock() {
        return new PatientAttachmentScopeLock(
                cases,
                invitations,
                sessions,
                attachments,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PreparationCase preparationCase() {
        PreparationCase value = PreparationCase.create(
                CASE_ID,
                ORGANIZATION_ID,
                new PatientReference("synthetic-patient"),
                Optional.of(new AppointmentReference("synthetic-appointment")),
                Optional.empty(),
                NOW.minusSeconds(600));
        value.startPatientPreparation(NOW.minusSeconds(300));
        value.releaseDomainEvents();
        return value;
    }

    private PatientInvitation invitation() {
        return PatientInvitation.restore(
                INVITATION_ID,
                ORGANIZATION_ID,
                CASE_ID,
                42L,
                DIGEST,
                PatientInvitationStatus.CONSUMED,
                NOW.minusSeconds(300),
                NOW.plusSeconds(3600),
                Optional.of(NOW.minusSeconds(240)),
                Optional.empty(),
                NOW.minusSeconds(240));
    }

    private PatientAccessSession session() {
        return PatientAccessSession.restore(
                SESSION_ID,
                INVITATION_ID,
                ORGANIZATION_ID,
                CASE_ID,
                DIGEST,
                NOW.minusSeconds(240),
                NOW.plusSeconds(3600),
                Optional.empty());
    }

    private PatientAccessSessionScope scope() {
        return new PatientAccessSessionScope(
                SESSION_ID,
                INVITATION_ID,
                ORGANIZATION_ID,
                CASE_ID,
                PatientAccessSessionScope.CONTRIBUTION_SCOPE,
                PatientInvitationStatus.CONSUMED,
                preparationCase.status(),
                NOW.minusSeconds(240),
                NOW.plusSeconds(3600),
                Optional.empty());
    }

    private PreparationAttachment existingAttachment() {
        return PreparationAttachment.uploadedByPatient(
                new AttachmentId(UUID.fromString("50000000-0000-0000-0000-000000000001")),
                ORGANIZATION_ID,
                CASE_ID,
                new AttachmentStorageKey(UUID.fromString("60000000-0000-0000-0000-000000000001")),
                "synthetic.pdf",
                AttachmentMediaType.PDF,
                42,
                new AttachmentDigest("a".repeat(64)),
                NOW.minusSeconds(60));
    }

    private StagedAttachment staged(String filename, String digestCharacter) {
        return new StagedAttachment(
                new AttachmentStorageKey(UUID.randomUUID()),
                filename,
                AttachmentMediaType.PDF,
                42,
                new AttachmentDigest(digestCharacter.repeat(64)));
    }
}
