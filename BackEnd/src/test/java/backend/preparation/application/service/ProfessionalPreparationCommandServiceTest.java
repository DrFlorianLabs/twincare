package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import backend.preparation.application.PreparationWorkflowConflictException;
import backend.preparation.application.port.in.ChangePreparationTaskStatusCommand;
import backend.preparation.application.port.in.ConfirmPreparationReadyCommand;
import backend.preparation.application.port.in.CreatePreparationTaskCommand;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.CurrentConsultationReasonReview;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationReadyConfirmationRepository;
import backend.preparation.application.port.out.PreparationTaskIdGenerator;
import backend.preparation.application.port.out.PreparationTaskRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadyConfirmation;
import backend.preparation.domain.PreparationReadinessPolicy;
import backend.preparation.domain.PreparationReadinessState;
import backend.preparation.domain.PreparationTask;
import backend.preparation.domain.PreparationTaskDescription;
import backend.preparation.domain.PreparationTaskId;
import backend.preparation.domain.PreparationTaskStatus;

class ProfessionalPreparationCommandServiceTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final PreparationTaskId TASK_ID = new PreparationTaskId(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final UUID REQUEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-08-21T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private ProfessionalPreparationAuthorization authorization;
    private PreparationCaseRepository preparationCases;
    private PreparationTaskRepository tasks;
    private PreparationReadyConfirmationRepository confirmations;
    private PreparationTaskIdGenerator taskIdGenerator;
    private CurrentConsultationReasonReview reasonReview;
    private ProfessionalPreparationCommandService service;

    @BeforeEach
    void setUp() {
        authorization = mock(ProfessionalPreparationAuthorization.class);
        preparationCases = mock(PreparationCaseRepository.class);
        tasks = mock(PreparationTaskRepository.class);
        confirmations = mock(PreparationReadyConfirmationRepository.class);
        taskIdGenerator = mock(PreparationTaskIdGenerator.class);
        reasonReview = mock(CurrentConsultationReasonReview.class);
        service = new ProfessionalPreparationCommandService(
                authorization,
                preparationCases,
                tasks,
                confirmations,
                taskIdGenerator,
                event -> { },
                new PreparationReadinessPolicy(),
                reasonReview,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(authorization.requireAccess(ORGANIZATION_ID, PreparationCaseCapability.MANAGE_PREPARATION_TASKS))
                .thenReturn(42L);
        when(authorization.requireAccess(
                ORGANIZATION_ID, PreparationCaseCapability.CONFIRM_READY_FOR_CONSULTATION))
                .thenReturn(42L);
    }

    @Test
    void rejectsReadinessConfirmationWhenTheCurrentReasonWasRejected() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.COLLECTING, 7);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase));
        when(reasonReview.isRejected(ORGANIZATION_ID, CASE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.confirm(
                new ConfirmPreparationReadyCommand(ORGANIZATION_ID, CASE_ID, 7)))
                .isInstanceOf(PreparationWorkflowConflictException.class);

        verify(preparationCases, never()).update(org.mockito.ArgumentMatchers.any());
        verify(confirmations, never()).add(org.mockito.ArgumentMatchers.any());
        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.COLLECTING);
    }

    @Test
    void createsAHumanTaskAndMovesTheCaseToNeedsAction() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.COLLECTING, 7);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.findByCreationRequestId(REQUEST_ID, CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(tasks.countAll(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);
        when(taskIdGenerator.nextId()).thenReturn(TASK_ID);

        var result = service.create(createCommand(Optional.of(44L)));

        assertThat(result.created()).isTrue();
        assertThat(result.task().assigneeUserId()).isEqualTo(44L);
        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.NEEDS_ACTION);
        verify(authorization).requireAssignableStaff(ORGANIZATION_ID, 44L);
        verify(tasks).add(org.mockito.ArgumentMatchers.any(PreparationTask.class));
        verify(preparationCases).update(preparationCase);
    }

    @Test
    void exactCreationRequestReplayReturnsTheOriginalTask() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.NEEDS_ACTION, 8);
        PreparationTask existing = openTask();
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.findByCreationRequestId(REQUEST_ID, CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(existing));

        var result = service.create(createCommand(Optional.of(44L)));

        assertThat(result.created()).isFalse();
        assertThat(result.task().id()).isEqualTo(TASK_ID);
        verify(tasks, never()).add(org.mockito.ArgumentMatchers.any());
        verify(preparationCases, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvingTheLastTaskReturnsTheCaseToReview() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.NEEDS_ACTION, 8);
        PreparationTask task = openTask();
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.findByIdForUpdate(TASK_ID, CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(task));
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);

        var result = service.changeStatus(new ChangePreparationTaskStatusCommand(
                ORGANIZATION_ID, CASE_ID, TASK_ID, PreparationTaskStatus.COMPLETED));

        assertThat(result.status()).isEqualTo(PreparationTaskStatus.COMPLETED);
        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.READY_FOR_REVIEW);
        verify(tasks).update(task);
        verify(preparationCases).update(preparationCase);
    }

    @Test
    void staleCaseRevisionBlocksReadinessConfirmation() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.COLLECTING, 7);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(new ConfirmPreparationReadyCommand(
                ORGANIZATION_ID, CASE_ID, 6)))
                .isInstanceOf(PreparationWorkflowConflictException.class);
        verify(confirmations, never()).add(org.mockito.ArgumentMatchers.any());
        verify(preparationCases, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void clinicianConfirmationPersistsReviewEvidenceAndMakesTheCaseReady() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.COLLECTING, 7);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());

        var result = service.confirm(new ConfirmPreparationReadyCommand(ORGANIZATION_ID, CASE_ID, 7));

        assertThat(result.caseStatus()).isEqualTo(PreparationCaseStatus.READY_FOR_CONSULTATION);
        assertThat(result.readiness().state()).isEqualTo(PreparationReadinessState.READY);
        assertThat(result.reviewedCaseRevision()).isEqualTo(7);
        var captor = ArgumentCaptor.forClass(PreparationReadyConfirmation.class);
        verify(confirmations).add(captor.capture());
        assertThat(captor.getValue().confirmedByUserId()).isEqualTo(42L);
        assertThat(captor.getValue().reviewedCaseRevision()).isEqualTo(7);
        assertThat(captor.getValue().reviewedReasonRecordedAt()).isEqualTo(CREATED_AT.plusSeconds(60));
        verify(preparationCases).update(preparationCase);
    }

    @Test
    void readinessConfirmationReplayPreservesTheOriginalClinicianAndTime() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.READY_FOR_CONSULTATION, 8);
        PreparationReadyConfirmation existing = confirmation(7, 99L);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(existing));

        var result = service.confirm(new ConfirmPreparationReadyCommand(ORGANIZATION_ID, CASE_ID, 7));

        assertThat(result.confirmedByUserId()).isEqualTo(99L);
        assertThat(result.confirmedAt()).isEqualTo(existing.confirmedAt());
        verify(confirmations, never()).add(org.mockito.ArgumentMatchers.any());
        verify(preparationCases, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readinessConfirmationReplayFailsClosedWhenTheCaseAdvancedAgain() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.READY_FOR_CONSULTATION, 9);
        PreparationReadyConfirmation existing = confirmation(7, 99L);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(0L);
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.confirm(new ConfirmPreparationReadyCommand(
                ORGANIZATION_ID, CASE_ID, 7)))
                .isInstanceOf(PreparationWorkflowConflictException.class);
    }

    @Test
    void openTasksBlockReadinessConfirmation() {
        PreparationCase preparationCase = caseAt(PreparationCaseStatus.NEEDS_ACTION, 8);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase));
        when(tasks.countOpen(CASE_ID, ORGANIZATION_ID)).thenReturn(1L);
        when(confirmations.findByCase(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(new ConfirmPreparationReadyCommand(
                ORGANIZATION_ID, CASE_ID, 8)))
                .isInstanceOf(PreparationWorkflowConflictException.class);
    }

    private CreatePreparationTaskCommand createCommand(Optional<Long> assignee) {
        return new CreatePreparationTaskCommand(
                ORGANIZATION_ID,
                CASE_ID,
                REQUEST_ID,
                new PreparationTaskDescription("Synthetic preparation task"),
                assignee);
    }

    private PreparationTask openTask() {
        return PreparationTask.create(
                TASK_ID,
                ORGANIZATION_ID,
                CASE_ID,
                REQUEST_ID,
                new PreparationTaskDescription("Synthetic preparation task"),
                44L,
                42L,
                CREATED_AT.plusSeconds(120));
    }

    private PreparationReadyConfirmation confirmation(long reviewedRevision, long actor) {
        return new PreparationReadyConfirmation(
                ORGANIZATION_ID,
                CASE_ID,
                actor,
                CREATED_AT.plusSeconds(180),
                PreparationReadinessPolicy.CURRENT_RULE_SET_ID,
                PreparationReadinessPolicy.CURRENT_RULE_SET_VERSION,
                reviewedRevision,
                CREATED_AT.plusSeconds(60),
                0);
    }

    private PreparationCase caseAt(PreparationCaseStatus status, long revision) {
        PatientStatement reason = PatientStatement.declaredByPatient(
                "Synthetic exact reason", CREATED_AT.plusSeconds(60));
        return PreparationCase.restore(
                CASE_ID,
                ORGANIZATION_ID,
                new PatientReference("synthetic-patient"),
                Optional.of(new AppointmentReference("synthetic-appointment")),
                Optional.empty(),
                CREATED_AT,
                status,
                Optional.of(reason),
                CREATED_AT.plusSeconds(60),
                revision);
    }
}
