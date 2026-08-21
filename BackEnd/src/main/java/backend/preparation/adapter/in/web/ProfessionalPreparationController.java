package backend.preparation.adapter.in.web;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import backend.exception.BadRequestException;
import backend.preparation.application.port.in.GetProfessionalPreparationViewCommand;
import backend.preparation.application.port.in.GetProfessionalPreparationViewUseCase;
import backend.preparation.application.port.in.ListProfessionalPreparationsCommand;
import backend.preparation.application.port.in.ListProfessionalPreparationsUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentResult;
import backend.preparation.application.port.in.ProfessionalPreparationListItemResult;
import backend.preparation.application.port.in.ProfessionalPreparationViewResult;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationReadinessAssessment;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases")
public class ProfessionalPreparationController {

    private final ListProfessionalPreparationsUseCase listPreparations;
    private final GetProfessionalPreparationViewUseCase getPreparationView;

    public ProfessionalPreparationController(
            ListProfessionalPreparationsUseCase listPreparations,
            GetProfessionalPreparationViewUseCase getPreparationView) {
        this.listPreparations = listPreparations;
        this.getPreparationView = getPreparationView;
    }

    @GetMapping
    public ResponseEntity<ProfessionalPreparationPageResponse> list(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListProfessionalPreparationsCommand command;
        try {
            command = new ListProfessionalPreparationsCommand(
                    new OrganizationId(organizationId),
                    page,
                    size);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Pagination de préparations invalide");
        }
        var result = listPreparations.list(command);
        var content = result.content().stream().map(this::toListItem).toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ProfessionalPreparationPageResponse(
                        content,
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()));
    }

    @GetMapping("/{caseId}/preparation-view")
    public ResponseEntity<ProfessionalPreparationViewResponse> get(
            @PathVariable String organizationId,
            @PathVariable String caseId) {
        GetProfessionalPreparationViewCommand command;
        try {
            command = new GetProfessionalPreparationViewCommand(
                    new OrganizationId(organizationId),
                    new PreparationCaseId(UUID.fromString(caseId)));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Identifiant de préparation invalide");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toView(getPreparationView.get(command)));
    }

    private ProfessionalPreparationListItemResponse toListItem(
            ProfessionalPreparationListItemResult result) {
        return new ProfessionalPreparationListItemResponse(
                result.caseId().value(),
                result.patientReference().value(),
                result.appointmentReference().map(reference -> reference.value()).orElse(null),
                result.scheduledAt().orElse(null),
                result.caseStatus().name(),
                toReadiness(result.readiness()),
                result.openTaskCount(),
                result.attachmentPresent(),
                result.updatedAt());
    }

    private ProfessionalPreparationViewResponse toView(ProfessionalPreparationViewResult result) {
        var reason = result.patientReason()
                .map(value -> new ProfessionalPatientReasonResponse(
                        true,
                        value.text(),
                        value.provenance().name(),
                        value.recordedAt()))
                .orElseGet(() -> new ProfessionalPatientReasonResponse(false, null, null, null));
        var attachment = result.attachment()
                .map(this::toAttachment)
                .orElseGet(() -> new ProfessionalAttachmentResponse(false, null, null, null, null, null));
        return new ProfessionalPreparationViewResponse(
                result.caseId().value(),
                result.patientReference().value(),
                result.appointmentReference().map(reference -> reference.value()).orElse(null),
                result.scheduledAt().orElse(null),
                result.caseStatus().name(),
                toReadiness(result.readiness()),
                reason,
                attachment,
                result.openTaskCount(),
                result.createdAt(),
                result.updatedAt());
    }

    private ProfessionalAttachmentResponse toAttachment(ProfessionalAttachmentResult result) {
        return new ProfessionalAttachmentResponse(
                true,
                result.displayFilename(),
                result.mediaType().contentType(),
                result.sizeBytes(),
                result.uploadedAt(),
                result.updatedAt());
    }

    private PreparationReadinessResponse toReadiness(PreparationReadinessAssessment readiness) {
        return new PreparationReadinessResponse(
                readiness.ruleSetId(),
                readiness.ruleSetVersion(),
                readiness.state().name(),
                readiness.blockers().stream().map(Enum::name).toList(),
                readiness.provenance().name());
    }
}
