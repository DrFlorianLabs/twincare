package backend.preparation.adapter.in.web;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import backend.exception.BadRequestException;
import backend.preparation.application.port.in.ChangePreparationTaskStatusCommand;
import backend.preparation.application.port.in.ChangePreparationTaskStatusUseCase;
import backend.preparation.application.port.in.ConfirmPreparationReadyCommand;
import backend.preparation.application.port.in.ConfirmPreparationReadyUseCase;
import backend.preparation.application.port.in.CreatePreparationTaskCommand;
import backend.preparation.application.port.in.CreatePreparationTaskUseCase;
import backend.preparation.application.port.in.PreparationTaskResult;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationTaskDescription;
import backend.preparation.domain.PreparationTaskId;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases/{caseId}")
public class ProfessionalPreparationCommandController {

    private final CreatePreparationTaskUseCase createTask;
    private final ChangePreparationTaskStatusUseCase changeTaskStatus;
    private final ConfirmPreparationReadyUseCase confirmReady;

    public ProfessionalPreparationCommandController(
            CreatePreparationTaskUseCase createTask,
            ChangePreparationTaskStatusUseCase changeTaskStatus,
            ConfirmPreparationReadyUseCase confirmReady) {
        this.createTask = createTask;
        this.changeTaskStatus = changeTaskStatus;
        this.confirmReady = confirmReady;
    }

    @PostMapping("/tasks")
    public ResponseEntity<PreparationTaskResponse> createTask(
            @PathVariable String organizationId,
            @PathVariable String caseId,
            @Valid @RequestBody CreatePreparationTaskRequest request) {
        var scope = scope(organizationId, caseId);
        CreatePreparationTaskCommand command;
        try {
            command = new CreatePreparationTaskCommand(
                    scope.organizationId(),
                    scope.caseId(),
                    request.requestId(),
                    new PreparationTaskDescription(request.label()),
                    Optional.ofNullable(request.assigneeUserId()));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Tâche de préparation invalide");
        }
        var result = createTask.create(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{taskId}")
                .buildAndExpand(result.task().id().value())
                .toUri();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .body(toTask(result.task()));
    }

    @PutMapping("/tasks/{taskId}/status")
    public ResponseEntity<PreparationTaskResponse> changeTaskStatus(
            @PathVariable String organizationId,
            @PathVariable String caseId,
            @PathVariable String taskId,
            @Valid @RequestBody ChangePreparationTaskStatusRequest request) {
        var scope = scope(organizationId, caseId);
        ChangePreparationTaskStatusCommand command;
        try {
            command = new ChangePreparationTaskStatusCommand(
                    scope.organizationId(),
                    scope.caseId(),
                    new PreparationTaskId(UUID.fromString(taskId)),
                    request.status());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Changement de tâche invalide");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toTask(changeTaskStatus.changeStatus(command)));
    }

    @PutMapping("/ready-confirmation")
    public ResponseEntity<PreparationReadyConfirmationResponse> confirmReady(
            @PathVariable String organizationId,
            @PathVariable String caseId,
            @Valid @RequestBody ConfirmPreparationReadyRequest request) {
        var scope = scope(organizationId, caseId);
        var result = confirmReady.confirm(new ConfirmPreparationReadyCommand(
                scope.organizationId(), scope.caseId(), request.expectedRevision()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PreparationReadyConfirmationResponse(
                        result.caseStatus().name(),
                        ProfessionalPreparationController.toReadinessResponse(result.readiness()),
                        result.confirmedByUserId(),
                        result.confirmedAt(),
                        result.ruleSetId(),
                        result.ruleSetVersion(),
                        result.reviewedCaseRevision()));
    }

    private PreparationScope scope(String organizationId, String caseId) {
        try {
            return new PreparationScope(
                    new OrganizationId(organizationId),
                    new PreparationCaseId(UUID.fromString(caseId)));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Identifiant de préparation invalide");
        }
    }

    static PreparationTaskResponse toTask(PreparationTaskResult result) {
        return new PreparationTaskResponse(
                result.id().value(),
                result.description().value(),
                result.assigneeUserId(),
                result.status().name(),
                result.createdByUserId(),
                result.createdAt(),
                result.closedByUserId().orElse(null),
                result.closedAt().orElse(null),
                result.updatedAt());
    }

    private record PreparationScope(OrganizationId organizationId, PreparationCaseId caseId) {
    }
}
