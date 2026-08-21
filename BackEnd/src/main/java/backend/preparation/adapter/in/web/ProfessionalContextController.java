package backend.preparation.adapter.in.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.preparation.application.port.in.GetProfessionalContextUseCase;

@RestController
@RequestMapping("/api/v1/professional-context")
public class ProfessionalContextController {

    private final GetProfessionalContextUseCase getContext;

    public ProfessionalContextController(GetProfessionalContextUseCase getContext) {
        this.getContext = getContext;
    }

    @GetMapping
    public ResponseEntity<ProfessionalContextResponse> get() {
        var result = getContext.get();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ProfessionalContextResponse(
                        result.actorUserId(),
                        result.displayName(),
                        result.organizations().stream()
                                .map(organization -> new ProfessionalOrganizationContextResponse(
                                        organization.organizationId().value(),
                                        organization.displayName(),
                                        organization.capabilities().stream().map(Enum::name).toList()))
                                .toList()));
    }
}
