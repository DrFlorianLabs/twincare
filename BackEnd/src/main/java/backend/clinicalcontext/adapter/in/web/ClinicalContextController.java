package backend.clinicalcontext.adapter.in.web;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.clinicalcontext.application.port.in.GetClinicalContextQuery;
import backend.clinicalcontext.application.port.in.GetClinicalContextUseCase;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactCommand;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactUseCase;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.exception.BadRequestException;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases/{caseId}/clinical-context")
public final class ClinicalContextController {

    private final GetClinicalContextUseCase getClinicalContext;
    private final ReviewClinicalFactUseCase reviewClinicalFact;

    public ClinicalContextController(
            GetClinicalContextUseCase getClinicalContext,
            ReviewClinicalFactUseCase reviewClinicalFact) {
        this.getClinicalContext = getClinicalContext;
        this.reviewClinicalFact = reviewClinicalFact;
    }

    @GetMapping
    public ResponseEntity<ClinicalContextResponse> get(
            @PathVariable String organizationId,
            @PathVariable UUID caseId) {
        var query = new GetClinicalContextQuery(
                organizationId(organizationId),
                new PreparationCaseId(caseId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClinicalContextResponseMapper.from(getClinicalContext.get(query)));
    }

    @PutMapping("/facts/{factId}/review")
    public ResponseEntity<ClinicalFactReviewResponse> review(
            @PathVariable String organizationId,
            @PathVariable UUID caseId,
            @PathVariable UUID factId,
            @Valid @RequestBody ClinicalFactReviewRequest request) {
        var result = reviewClinicalFact.review(new ReviewClinicalFactCommand(
                organizationId(organizationId),
                new PreparationCaseId(caseId),
                new ClinicalFactId(factId),
                request.decision()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ClinicalFactReviewResponse(
                        result.factId().value(),
                        result.status().name(),
                        result.reviewedAt()));
    }

    private OrganizationId organizationId(String value) {
        try {
            return new OrganizationId(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Organisation invalide");
        }
    }

}
