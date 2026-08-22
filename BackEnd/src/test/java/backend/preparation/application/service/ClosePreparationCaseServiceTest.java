package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import backend.preparation.application.PreparationWorkflowConflictException;
import backend.preparation.application.port.in.ClosePreparationCaseCommand;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseClosureRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationReadyConfirmationRepository;
import backend.preparation.application.port.out.PreparationTaskRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseClosure;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadinessPolicy;
import backend.preparation.domain.PreparationReadyConfirmation;
import backend.preparation.domain.TokenDigest;

class ClosePreparationCaseServiceTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final PatientInvitationId INVITATION_ID = new PatientInvitationId(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final Instant CREATED_AT = Instant.parse("2026-08-21T09:00:00Z");
    private static final Instant REASON_AT = Instant.parse("2026-08-21T09:05:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-21T09:30:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-08-21T10:00:00Z");

    private ProfessionalPreparationAuthorization authorization;
    private PreparationCaseRepository preparationCases;
    private PreparationReadyConfirmationRepository confirmations;
    private PreparationTaskRepository tasks;
    private PatientInvitationRepository invitations;
    private PatientAccessSessionRepository sessions;
    private PreparationCaseClosureRepository closures;
    private PreparationAuditEventStore auditEvents;
    private ClosePreparationCaseService service;

    @BeforeEach
    void setUp() {
        authorization = mock(ProfessionalPreparationAuthorization.class);
        preparationCases = mock(PreparationCaseRepository.class);
        confirmations = mock(PreparationReadyConfirmationRepository.class);
        tasks = mock(PreparationTaskRepository.class);
        invitations = mock(PatientInvitationRepository.class);
        sessions = mock(PatientAccessSessionRepository.class);
        closures = mock(PreparationCaseClosureRepository.class);
        auditEvents = mock(PreparationAuditEventStore.class);
        service = new ClosePreparationCaseService(
                authorization,
                preparationCases,
                confirmations,
                tasks,
                invitations,
                sessions,
                closures,
                auditEvents,
                Clock.fixed(CLOSED_AT, ZoneOffset.UTC));
        when(authorization.requireAccess(
                ORGANIZATION_ID, PreparationCaseCapability.CLOSE_PREPARATION_CASE))
                .thenReturn(42L);
    }

    @Test
    void closesReadyCaseAndRevokesRemainingPatientCredentialsAtomically() {
        PreparationCase preparationCase = readyCase(8);
        PatientInvitation invitation = consumedInvitation();
        PatientAccessSession session = activeSession();
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase));
        when(closures.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(confirmation()));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);
        when(invitations.findAllByCaseForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(List.of(invitation));
        when(sessions.findAllByCaseForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(List.of(session));
        when(preparationCases.update(preparationCase)).thenReturn(9L);

        var result = service.close(new ClosePreparationCaseCommand(
                ORGANIZATION_ID, CASE_ID, 8));

        assertThat(result.caseStatus()).isEqualTo(PreparationCaseStatus.CLOSED);
        assertThat(result.revision()).isEqualTo(9);
        assertThat(result.readyCaseRevision()).isEqualTo(8);
        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.CLOSED);
        assertThat(invitation.status()).isEqualTo(PatientInvitationStatus.REVOKED);
        assertThat(session.revokedAt()).contains(CLOSED_AT);

        var closureCaptor = ArgumentCaptor.forClass(PreparationCaseClosure.class);
        verify(closures).add(closureCaptor.capture());
        assertThat(closureCaptor.getValue().closedByUserId()).isEqualTo(42);
        assertThat(closureCaptor.getValue().closedCaseRevision()).isEqualTo(9);

        var eventCaptor = ArgumentCaptor.forClass(PreparationAuditEvent.class);
        verify(auditEvents, times(3)).append(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(PreparationAuditEvent::type)
                .containsExactly(
                        PreparationAuditEventType.INVITATION_REVOKED,
                        PreparationAuditEventType.PATIENT_ACCESS_REVOKED,
                        PreparationAuditEventType.PREPARATION_CLOSED);
        assertThat(eventCaptor.getAllValues())
                .allMatch(event -> event.caseRevision() == 9 && event.actorUserId().orElseThrow() == 42);
    }

    @Test
    void staleRevisionFailsBeforeCredentialLocksOrMutation() {
        PreparationCase preparationCase = readyCase(8);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase));
        when(closures.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(confirmation()));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.close(new ClosePreparationCaseCommand(
                ORGANIZATION_ID, CASE_ID, 7)))
                .isInstanceOf(PreparationWorkflowConflictException.class);

        verify(invitations, never()).findAllByCaseForUpdate(any(), any());
        verify(preparationCases, never()).update(any());
        verify(auditEvents, never()).append(any());
    }

    @Test
    void exactReplayReturnsImmutableClosureWithoutAnotherRevisionOrAuditEvent() {
        PreparationCase preparationCase = closedCase(9);
        PreparationCaseClosure closure = new PreparationCaseClosure(
                ORGANIZATION_ID, CASE_ID, 84L, CLOSED_AT.minusSeconds(60), 8, 9);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase));
        when(closures.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(closure));

        var result = service.close(new ClosePreparationCaseCommand(
                ORGANIZATION_ID, CASE_ID, 8));

        assertThat(result.closedByUserId()).isEqualTo(84L);
        assertThat(result.closedAt()).isEqualTo(CLOSED_AT.minusSeconds(60));
        verify(preparationCases, never()).update(any());
        verify(invitations, never()).findAllByCaseForUpdate(any(), any());
        verify(auditEvents, never()).append(any());
    }

    @Test
    void inconsistentReadinessEvidenceFailsClosed() {
        PreparationCase preparationCase = readyCase(8);
        var staleConfirmation = new PreparationReadyConfirmation(
                ORGANIZATION_ID,
                CASE_ID,
                42L,
                CONFIRMED_AT,
                PreparationReadinessPolicy.CURRENT_RULE_SET_ID,
                PreparationReadinessPolicy.CURRENT_RULE_SET_VERSION,
                6,
                REASON_AT,
                0);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase));
        when(closures.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(staleConfirmation));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.close(new ClosePreparationCaseCommand(
                ORGANIZATION_ID, CASE_ID, 8)))
                .isInstanceOf(PreparationWorkflowConflictException.class);
        verify(preparationCases, never()).update(any());
    }

    @Test
    void closureCommandRejectsARevisionThatCanNeverBeReady() {
        assertThatThrownBy(() -> new ClosePreparationCaseCommand(
                ORGANIZATION_ID, CASE_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PreparationReadyConfirmation confirmation() {
        return new PreparationReadyConfirmation(
                ORGANIZATION_ID,
                CASE_ID,
                42L,
                CONFIRMED_AT,
                PreparationReadinessPolicy.CURRENT_RULE_SET_ID,
                PreparationReadinessPolicy.CURRENT_RULE_SET_VERSION,
                7,
                REASON_AT,
                0);
    }

    private PreparationCase readyCase(long revision) {
        return caseAt(PreparationCaseStatus.READY_FOR_CONSULTATION, CONFIRMED_AT, revision);
    }

    private PreparationCase closedCase(long revision) {
        return caseAt(PreparationCaseStatus.CLOSED, CLOSED_AT.minusSeconds(60), revision);
    }

    private PreparationCase caseAt(PreparationCaseStatus status, Instant updatedAt, long revision) {
        return PreparationCase.restore(
                CASE_ID,
                ORGANIZATION_ID,
                new PatientReference("synthetic-patient"),
                Optional.of(new AppointmentReference("synthetic-appointment")),
                Optional.empty(),
                CREATED_AT,
                status,
                Optional.of(PatientStatement.declaredByPatient("Synthetic reason", REASON_AT)),
                updatedAt,
                revision);
    }

    private PatientInvitation consumedInvitation() {
        return PatientInvitation.restore(
                INVITATION_ID,
                ORGANIZATION_ID,
                CASE_ID,
                42L,
                new TokenDigest("test-key", "a".repeat(64)),
                PatientInvitationStatus.CONSUMED,
                CREATED_AT,
                CLOSED_AT.plusSeconds(3600),
                Optional.of(REASON_AT),
                Optional.empty(),
                REASON_AT);
    }

    private PatientAccessSession activeSession() {
        return PatientAccessSession.restore(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                INVITATION_ID,
                ORGANIZATION_ID,
                CASE_ID,
                new TokenDigest("test-key", "b".repeat(64)),
                REASON_AT,
                CLOSED_AT.plusSeconds(3600),
                Optional.empty());
    }
}
