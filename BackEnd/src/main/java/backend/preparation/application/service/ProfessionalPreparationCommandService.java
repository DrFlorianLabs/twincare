package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.PreparationTaskNotFoundException;
import backend.preparation.application.PreparationWorkflowConflictException;
import backend.preparation.application.port.in.ChangePreparationTaskStatusCommand;
import backend.preparation.application.port.in.ChangePreparationTaskStatusUseCase;
import backend.preparation.application.port.in.ConfirmPreparationReadyCommand;
import backend.preparation.application.port.in.ConfirmPreparationReadyUseCase;
import backend.preparation.application.port.in.CreatePreparationTaskCommand;
import backend.preparation.application.port.in.CreatePreparationTaskResult;
import backend.preparation.application.port.in.CreatePreparationTaskUseCase;
import backend.preparation.application.port.in.PreparationReadyConfirmationResult;
import backend.preparation.application.port.in.PreparationTaskResult;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationReadyConfirmationRepository;
import backend.preparation.application.port.out.PreparationTaskIdGenerator;
import backend.preparation.application.port.out.PreparationTaskRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadyConfirmation;
import backend.preparation.domain.PreparationReadinessPolicy;
import backend.preparation.domain.PreparationReadinessState;
import backend.preparation.domain.PreparationTask;

public final class ProfessionalPreparationCommandService implements
        CreatePreparationTaskUseCase,
        ChangePreparationTaskStatusUseCase,
        ConfirmPreparationReadyUseCase {

    public static final int MAX_TASKS_PER_CASE = 100;

    private final ProfessionalPreparationAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PreparationTaskRepository tasks;
    private final PreparationReadyConfirmationRepository confirmations;
    private final PreparationTaskIdGenerator taskIdGenerator;
    private final PreparationReadinessPolicy readinessPolicy;
    private final Clock clock;

    public ProfessionalPreparationCommandService(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationTaskRepository tasks,
            PreparationReadyConfirmationRepository confirmations,
            PreparationTaskIdGenerator taskIdGenerator,
            PreparationReadinessPolicy readinessPolicy,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.tasks = Objects.requireNonNull(tasks);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.taskIdGenerator = Objects.requireNonNull(taskIdGenerator);
        this.readinessPolicy = Objects.requireNonNull(readinessPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public CreatePreparationTaskResult create(CreatePreparationTaskCommand command) {
        Objects.requireNonNull(command, "Preparation task command is required");
        long actorUserId = authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.MANAGE_PREPARATION_TASKS);
        PreparationCase preparationCase = lockCase(command.organizationId(), command.caseId());
        long assigneeUserId = command.assigneeUserId().orElse(actorUserId);

        var replay = tasks.findByCreationRequestId(
                command.requestId(), preparationCase.id(), preparationCase.organizationId());
        if (replay.isPresent()) {
            PreparationTask existing = replay.get();
            if (!existing.description().equals(command.description())
                    || existing.assigneeUserId() != assigneeUserId) {
                throw new PreparationWorkflowConflictException();
            }
            return new CreatePreparationTaskResult(toResult(existing), false);
        }

        if (assigneeUserId != actorUserId) {
            authorization.requireAssignableStaff(command.organizationId(), assigneeUserId);
        }
        requireTaskCreationState(preparationCase);
        if (tasks.countAll(preparationCase.id(), preparationCase.organizationId()) >= MAX_TASKS_PER_CASE) {
            throw new PreparationWorkflowConflictException();
        }

        Instant now = now();
        PreparationTask task = PreparationTask.create(
                taskIdGenerator.nextId(),
                preparationCase.organizationId(),
                preparationCase.id(),
                command.requestId(),
                command.description(),
                assigneeUserId,
                actorUserId,
                now);
        tasks.add(task);
        if (preparationCase.status() == PreparationCaseStatus.NEEDS_ACTION) {
            preparationCase.touchVisibleContent(now);
        } else {
            preparationCase.markNeedsAction(now);
        }
        preparationCases.update(preparationCase);
        return new CreatePreparationTaskResult(toResult(task), true);
    }

    @Override
    public PreparationTaskResult changeStatus(ChangePreparationTaskStatusCommand command) {
        Objects.requireNonNull(command, "Preparation task status command is required");
        long actorUserId = authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.MANAGE_PREPARATION_TASKS);
        PreparationCase preparationCase = lockCase(command.organizationId(), command.caseId());
        PreparationTask task = tasks.findByIdForUpdate(
                        command.taskId(), preparationCase.id(), preparationCase.organizationId())
                .orElseThrow(PreparationTaskNotFoundException::new);

        if (task.status() == command.targetStatus()) {
            return toResult(task);
        }
        if (task.status().isTerminal()
                || preparationCase.status() != PreparationCaseStatus.NEEDS_ACTION
                || confirmations.findByCase(preparationCase.id(), preparationCase.organizationId()).isPresent()) {
            throw new PreparationWorkflowConflictException();
        }

        Instant now = now();
        try {
            task.close(command.targetStatus(), actorUserId, now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new PreparationWorkflowConflictException();
        }
        tasks.update(task);
        long openTaskCount = tasks.countOpen(preparationCase.id(), preparationCase.organizationId());
        if (openTaskCount == 0) {
            preparationCase.markReadyForReview(now);
        } else {
            preparationCase.touchVisibleContent(now);
        }
        preparationCases.update(preparationCase);
        return toResult(task);
    }

    @Override
    public PreparationReadyConfirmationResult confirm(ConfirmPreparationReadyCommand command) {
        Objects.requireNonNull(command, "Readiness confirmation command is required");
        long actorUserId = authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.CONFIRM_READY_FOR_CONSULTATION);
        PreparationCase preparationCase = lockCase(command.organizationId(), command.caseId());
        long openTaskCount = tasks.countOpen(preparationCase.id(), preparationCase.organizationId());
        var existing = confirmations.findByCase(preparationCase.id(), preparationCase.organizationId());
        if (existing.isPresent()) {
            PreparationReadyConfirmation confirmation = existing.get();
            if (preparationCase.status() != PreparationCaseStatus.READY_FOR_CONSULTATION
                    || confirmation.reviewedCaseRevision() != command.expectedRevision()
                    || preparationCase.revision() <= 0
                    || confirmation.reviewedCaseRevision() != preparationCase.revision() - 1
                    || openTaskCount != 0
                    || confirmation.openTaskCount() != 0
                    || preparationCase.patientReason()
                            .filter(reason -> reason.recordedAt().equals(
                                    confirmation.reviewedReasonRecordedAt()))
                            .isEmpty()
                    || confirmation.confirmedAt().isBefore(confirmation.reviewedReasonRecordedAt())) {
                throw new PreparationWorkflowConflictException();
            }
            return confirmationResult(preparationCase, confirmation, openTaskCount);
        }

        if (preparationCase.revision() != command.expectedRevision()
                || (preparationCase.status() != PreparationCaseStatus.COLLECTING
                        && preparationCase.status() != PreparationCaseStatus.READY_FOR_REVIEW)
                || openTaskCount != 0) {
            throw new PreparationWorkflowConflictException();
        }
        var beforeConfirmation = readinessPolicy.assessCurrent(
                preparationCase.status(),
                preparationCase.patientReason().isPresent(),
                Math.toIntExact(openTaskCount),
                false);
        if (beforeConfirmation.state() != PreparationReadinessState.REVIEWABLE) {
            throw new PreparationWorkflowConflictException();
        }

        Instant now = now();
        PreparationReadyConfirmation confirmation = new PreparationReadyConfirmation(
                preparationCase.organizationId(),
                preparationCase.id(),
                actorUserId,
                now,
                PreparationReadinessPolicy.CURRENT_RULE_SET_ID,
                PreparationReadinessPolicy.CURRENT_RULE_SET_VERSION,
                preparationCase.revision(),
                preparationCase.patientReason().orElseThrow().recordedAt(),
                0);
        if (preparationCase.status() == PreparationCaseStatus.COLLECTING) {
            preparationCase.markReadyForReview(now);
        }
        preparationCase.markReadyForConsultation(now);
        preparationCases.update(preparationCase);
        confirmations.add(confirmation);
        return confirmationResult(preparationCase, confirmation, 0);
    }

    private PreparationCase lockCase(
            backend.preparation.domain.OrganizationId organizationId,
            backend.preparation.domain.PreparationCaseId caseId) {
        return preparationCases.findByIdForUpdate(caseId, organizationId)
                .orElseThrow(PreparationCaseNotFoundException::new);
    }

    private void requireTaskCreationState(PreparationCase preparationCase) {
        boolean allowedStatus = preparationCase.status() == PreparationCaseStatus.COLLECTING
                || preparationCase.status() == PreparationCaseStatus.READY_FOR_REVIEW
                || preparationCase.status() == PreparationCaseStatus.NEEDS_ACTION;
        if (!allowedStatus
                || preparationCase.patientReason().isEmpty()
                || confirmations.findByCase(preparationCase.id(), preparationCase.organizationId()).isPresent()) {
            throw new PreparationWorkflowConflictException();
        }
    }

    private PreparationReadyConfirmationResult confirmationResult(
            PreparationCase preparationCase,
            PreparationReadyConfirmation confirmation,
            long openTaskCount) {
        var readiness = readinessPolicy.assess(
                confirmation.ruleSetId(),
                confirmation.ruleSetVersion(),
                preparationCase.status(),
                preparationCase.patientReason().isPresent(),
                Math.toIntExact(openTaskCount),
                true);
        return new PreparationReadyConfirmationResult(
                preparationCase.status(),
                readiness,
                confirmation.confirmedByUserId(),
                confirmation.confirmedAt(),
                confirmation.ruleSetId(),
                confirmation.ruleSetVersion(),
                confirmation.reviewedCaseRevision());
    }

    private PreparationTaskResult toResult(PreparationTask task) {
        return new PreparationTaskResult(
                task.id(),
                task.description(),
                task.assigneeUserId(),
                task.status(),
                task.createdByUserId(),
                task.createdAt(),
                task.closedByUserId(),
                task.closedAt(),
                task.updatedAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
