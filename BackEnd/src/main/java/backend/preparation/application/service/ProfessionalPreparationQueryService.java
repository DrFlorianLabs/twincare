package backend.preparation.application.service;

import java.util.Objects;

import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.GetProfessionalPreparationViewCommand;
import backend.preparation.application.port.in.GetProfessionalPreparationViewUseCase;
import backend.preparation.application.port.in.ListProfessionalPreparationsCommand;
import backend.preparation.application.port.in.ListProfessionalPreparationsUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentResult;
import backend.preparation.application.port.in.ProfessionalPreparationListItemResult;
import backend.preparation.application.port.in.ProfessionalPreparationPageResult;
import backend.preparation.application.port.in.ProfessionalPreparationViewResult;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.ProfessionalPreparationQueryRepository;
import backend.preparation.domain.PreparationReadinessPolicy;

public final class ProfessionalPreparationQueryService implements
        ListProfessionalPreparationsUseCase,
        GetProfessionalPreparationViewUseCase {

    private static final int NO_OPEN_TASKS = 0;
    private static final boolean NO_READY_CONFIRMATION_EVIDENCE = false;

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
                        readinessPolicy.assessCurrent(
                                snapshot.caseStatus(),
                                snapshot.patientReasonPresent(),
                                NO_OPEN_TASKS,
                                NO_READY_CONFIRMATION_EVIDENCE),
                        NO_OPEN_TASKS,
                        snapshot.attachmentPresent(),
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
        var readiness = readinessPolicy.assessCurrent(
                snapshot.caseStatus(),
                snapshot.patientReason().isPresent(),
                NO_OPEN_TASKS,
                NO_READY_CONFIRMATION_EVIDENCE);
        var attachment = snapshot.attachment().map(value -> new ProfessionalAttachmentResult(
                value.displayFilename(),
                value.mediaType(),
                value.sizeBytes(),
                value.uploadedAt(),
                value.updatedAt()));
        return new ProfessionalPreparationViewResult(
                snapshot.caseId(),
                snapshot.patientReference(),
                snapshot.appointmentReference(),
                snapshot.scheduledAt(),
                snapshot.caseStatus(),
                readiness,
                snapshot.patientReason(),
                attachment,
                NO_OPEN_TASKS,
                snapshot.createdAt(),
                snapshot.updatedAt());
    }

}
