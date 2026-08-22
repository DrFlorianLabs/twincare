package backend.preparation.adapter.in.web;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.exception.BadRequestException;
import backend.preparation.application.port.in.ClosePreparationCaseCommand;
import backend.preparation.application.port.in.ClosePreparationCaseUseCase;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases/{caseId}")
public class ProfessionalPreparationClosureController {

    private final ClosePreparationCaseUseCase closePreparationCase;

    public ProfessionalPreparationClosureController(ClosePreparationCaseUseCase closePreparationCase) {
        this.closePreparationCase = closePreparationCase;
    }

    @PutMapping("/closure")
    public ResponseEntity<ClosePreparationCaseResponse> close(
            @PathVariable String organizationId,
            @PathVariable String caseId,
            @Valid @RequestBody ClosePreparationCaseRequest request) {
        ClosePreparationCaseCommand command;
        try {
            command = new ClosePreparationCaseCommand(
                    new OrganizationId(organizationId),
                    new PreparationCaseId(UUID.fromString(caseId)),
                    request.expectedRevision());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Clôture de préparation invalide");
        }
        var result = closePreparationCase.close(command);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ClosePreparationCaseResponse(
                        result.caseStatus().name(),
                        result.revision(),
                        result.closedByUserId(),
                        result.closedAt(),
                        result.readyCaseRevision()));
    }
}
