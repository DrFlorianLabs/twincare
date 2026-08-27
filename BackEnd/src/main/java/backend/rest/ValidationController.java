package backend.rest;

import backend.dto.ValidationRequest;
import backend.entity.Validation;
import backend.service.ValidationService;
import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("legacy-prototype")
@RestController
@RequestMapping("/api/validations")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/document/{documentId}")
    public Validation validerDocument(@PathVariable Long documentId,
                                      @Valid @RequestBody ValidationRequest request) {
        return validationService.validerDocument(documentId, request);
    }
}
