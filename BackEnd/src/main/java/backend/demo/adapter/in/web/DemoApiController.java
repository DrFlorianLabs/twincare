package backend.demo.adapter.in.web;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.clinicalcontext.adapter.in.web.ClinicalContextResponse;
import backend.clinicalcontext.adapter.in.web.ClinicalContextResponseMapper;
import backend.clinicalcontext.domain.FactReviewDecision;
import backend.demo.application.DemoScenarioService;
import jakarta.validation.Valid;

@RestController
@Profile("demo")
@RequestMapping("/demo/api")
public final class DemoApiController {

    private final DemoScenarioService scenario;

    public DemoApiController(DemoScenarioService scenario) {
        this.scenario = scenario;
    }

    @GetMapping("/health")
    public ResponseEntity<Void> health() {
        scenario.read();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/state")
    public ResponseEntity<ClinicalContextResponse> state() {
        return noStore(ClinicalContextResponseMapper.from(scenario.read()));
    }

    @PutMapping("/reason")
    public ResponseEntity<ClinicalContextResponse> reason(
            @Valid @RequestBody DemoReasonRequest request) {
        return noStore(ClinicalContextResponseMapper.from(
                scenario.recordPatientReason(request.reason())));
    }

    @PutMapping("/facts/{factId}/review/{decision}")
    public ResponseEntity<ClinicalContextResponse> review(
            @PathVariable UUID factId,
            @PathVariable FactReviewDecision decision) {
        return noStore(ClinicalContextResponseMapper.from(
                scenario.reviewFact(factId, decision)));
    }

    @PostMapping("/reset")
    public ResponseEntity<ClinicalContextResponse> reset() {
        return noStore(ClinicalContextResponseMapper.from(scenario.reset()));
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClinicalContextResponse> export() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=twincare-clinical-context-v1.json")
                .body(ClinicalContextResponseMapper.from(scenario.read()));
    }

    private ResponseEntity<ClinicalContextResponse> noStore(ClinicalContextResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
