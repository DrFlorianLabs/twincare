package backend.preparation.adapter.in.web;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import backend.exception.BadRequestException;
import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.in.CreatePreparationCaseResult;
import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases")
public class PreparationCaseController {

    private final CreatePreparationCaseUseCase createPreparationCase;

    public PreparationCaseController(CreatePreparationCaseUseCase createPreparationCase) {
        this.createPreparationCase = createPreparationCase;
    }

    @PostMapping
    public ResponseEntity<CreatePreparationCaseResponse> create(
            @PathVariable String organizationId,
            @Valid @RequestBody CreatePreparationCaseRequest request) {
        CreatePreparationCaseCommand command;
        try {
            command = new CreatePreparationCaseCommand(
                    request.requestId(),
                    new OrganizationId(organizationId),
                    new PatientReference(request.patientReference()),
                    optionalAppointmentReference(request.appointmentReference()),
                    Optional.ofNullable(request.scheduledAt()));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Requête de création de préparation invalide");
        }
        CreatePreparationCaseResult result = createPreparationCase.create(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{caseId}/preparation-view")
                .buildAndExpand(result.id().value())
                .toUri();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .body(new CreatePreparationCaseResponse(
                        result.id().value(),
                        result.status().name(),
                        result.createdAt()));
    }

    private Optional<AppointmentReference> optionalAppointmentReference(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new AppointmentReference(value));
    }
}
