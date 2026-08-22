package backend.preparation.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.GetProfessionalPreparationViewCommand;
import backend.preparation.application.port.in.GetProfessionalPreparationViewUseCase;
import backend.preparation.application.port.in.ListProfessionalPreparationsCommand;
import backend.preparation.application.port.in.ListProfessionalPreparationsUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentResult;
import backend.preparation.application.port.in.ProfessionalPreparationListItemResult;
import backend.preparation.application.port.in.ProfessionalPreparationPageResult;
import backend.preparation.application.port.in.ProfessionalPreparationViewResult;
import backend.preparation.application.port.in.ProfessionalReadyConfirmationResult;
import backend.preparation.application.port.in.PreparationTaskResult;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.ProfessionalPreparationClosureSnapshot;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.ProfessionalPreparationQueryRepository;
import backend.preparation.application.port.out.ProfessionalReadyConfirmationSnapshot;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadinessAssessment;
import backend.preparation.domain.PreparationReadinessPolicy;

public final class ProfessionalPreparationQueryService implements
        ListProfessionalPreparationsUseCase,
        GetProfessionalPreparationViewUseCase {

    private final ProfessionalPreparationAuthorization authorization;
    private final ProfessionalPreparationQueryRepository repository;
    private final PreparationReadinessPolicy readinessPolicy;

    public ProfessionalPreparationQueryService(
            ProfessionalPreparationAuthorization authorization,
            ProfessionalPreparationQueryRepository repository,
            PreparationReadinessPolicy readinessPolicy) {
        this.authorization = Objects.requireNonNull(authorization);
        this.repository = Objects.requireNonNull(repository);
        this.readinessPolicy = Objects.requireNonNull(readinessPolicy);
    }

    @Override
    public ProfessionalPreparationPageResult list(ListProfessionalPreparationsCommand command) {
        Objects.requireNonNull(command, "Preparation list command is required");
        authorization.requireAccess(command.organizationId(), PreparationCaseCapability.VIEW_PREPARATION);
        var page = repository.findPage(command.organizationId(), command.page(), command.size());
        var content = page.content().stream()
                .map(snapshot -> new ProfessionalPreparationListItemResult(
                        snapshot.caseId(),
                        snapshot.patientReference(),
                        snapshot.appointmentReference(),
                        snapshot.scheduledAt(),
                        snapshot.caseStatus(),
                        assess(
                                snapshot.caseStatus(),
                                snapshot.patientReasonPresent(),
                                snapshot.patientReasonRecordedAt(),
                                snapshot.openTaskCount(),
                                snapshot.readyConfirmation(),
                                snapshot.closure(),
                                snapshot.revision(),
                                snapshot.updatedAt()),
                        snapshot.openTaskCount(),
                        snapshot.attachmentPresent(),
                        snapshot.revision(),
                        snapshot.updatedAt()))
                .toList();
        return new ProfessionalPreparationPageResult(
                content,
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }

    @Override
    public ProfessionalPreparationViewResult get(GetProfessionalPreparationViewCommand command) {
        Objects.requireNonNull(command, "Preparation view command is required");
        authorization.requireAccess(command.organizationId(), PreparationCaseCapability.VIEW_PREPARATION);
        var snapshot = repository.findView(command.organizationId(), command.caseId())
                .orElseThrow(PreparationCaseNotFoundException::new);
        var readiness = assess(
                snapshot.caseStatus(),
                snapshot.patientReason().isPresent(),
                snapshot.patientReason().map(value -> value.recordedAt()),
                snapshot.openTaskCount(),
                snapshot.readyConfirmation(),
                snapshot.closure(),
                snapshot.revision(),
                snapshot.updatedAt());
        var attachment = snapshot.attachment().map(value -> new ProfessionalAttachmentResult(
                value.displayFilename(),
                value.mediaType(),
                value.sizeBytes(),
                value.uploadedAt(),
                value.updatedAt()));
        var taskResults = snapshot.tasks().stream()
                .map(task -> new PreparationTaskResult(
                        task.id(),
                        task.description(),
                        task.assigneeUserId(),
                        task.status(),
                        task.createdByUserId(),
                        task.createdAt(),
                        task.closedByUserId(),
                        task.closedAt(),
                        task.updatedAt()))
                .toList();
        var confirmation = snapshot.readyConfirmation().map(value -> new ProfessionalReadyConfirmationResult(
                value.confirmedByUserId(),
                value.confirmedAt(),
                value.ruleSetId(),
                value.ruleSetVersion(),
                value.reviewedCaseRevision()));
        return new ProfessionalPreparationViewResult(
                snapshot.caseId(),
                snapshot.patientReference(),
                snapshot.appointmentReference(),
                snapshot.scheduledAt(),
                snapshot.caseStatus(),
                readiness,
                snapshot.patientReason(),
                attachment,
                taskResults,
                confirmation,
                snapshot.openTaskCount(),
                snapshot.revision(),
                snapshot.createdAt(),
                snapshot.updatedAt());
    }

    private PreparationReadinessAssessment assess(
            PreparationCaseStatus caseStatus,
            boolean patientReasonPresent,
            Optional<Instant> patientReasonRecordedAt,
            int openTaskCount,
            Optional<ProfessionalReadyConfirmationSnapshot> confirmation,
            Optional<ProfessionalPreparationClosureSnapshot> closure,
            long caseRevision,
            Instant caseUpdatedAt) {
        validateClosureEvidence(caseStatus, confirmation, closure, caseRevision, caseUpdatedAt);
        return confirmation
                .map(value -> {
                    long expectedRevisionDistance = caseStatus.isTerminal() ? 2 : 1;
                    boolean synchronizedEvidence = openTaskCount == 0
                            && value.openTaskCount() == 0
                            && patientReasonPresent
                            && patientReasonRecordedAt
                                    .filter(value.reviewedReasonRecordedAt()::equals)
                                    .isPresent()
                            && !value.confirmedAt().isBefore(value.reviewedReasonRecordedAt())
                            && caseRevision >= expectedRevisionDistance
                            && value.reviewedCaseRevision()
                                    == caseRevision - expectedRevisionDistance;
                    if (!synchronizedEvidence) {
                        throw new IllegalStateException("Readiness confirmation evidence is inconsistent");
                    }
                    return readinessPolicy.assess(
                            value.ruleSetId(),
                            value.ruleSetVersion(),
                            caseStatus,
                            patientReasonPresent,
                            openTaskCount,
                            true);
                })
                .orElseGet(() -> readinessPolicy.assessCurrent(
                        caseStatus,
                        patientReasonPresent,
                        openTaskCount,
                        false));
    }

    private void validateClosureEvidence(
            PreparationCaseStatus caseStatus,
            Optional<ProfessionalReadyConfirmationSnapshot> confirmation,
            Optional<ProfessionalPreparationClosureSnapshot> closure,
            long caseRevision,
            Instant caseUpdatedAt) {
        if (caseStatus != PreparationCaseStatus.CLOSED) {
            if (closure.isPresent()) {
                throw new IllegalStateException("Closure evidence is inconsistent");
            }
            return;
        }
        var evidence = closure.orElseThrow(
                () -> new IllegalStateException("Closure evidence is missing"));
        if (confirmation.isEmpty()
                || evidence.closedCaseRevision() != caseRevision
                || evidence.readyCaseRevision() != caseRevision - 1
                || !evidence.closedAt().equals(caseUpdatedAt)) {
            throw new IllegalStateException("Closure evidence is inconsistent");
        }
    }

}
