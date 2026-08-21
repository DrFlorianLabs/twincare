package backend.preparation.application.service;

import backend.preparation.application.port.in.GetProfessionalContextUseCase;
import backend.preparation.application.port.in.ProfessionalContextResult;
import backend.preparation.application.port.in.ProfessionalOrganizationContextResult;
import backend.preparation.application.port.out.ProfessionalContextSource;

public final class GetProfessionalContextService implements GetProfessionalContextUseCase {

    private final ProfessionalContextSource source;

    public GetProfessionalContextService(ProfessionalContextSource source) {
        this.source = source;
    }

    @Override
    public ProfessionalContextResult get() {
        var snapshot = source.loadCurrent();
        return new ProfessionalContextResult(
                snapshot.actorUserId(),
                snapshot.displayName(),
                snapshot.organizations().stream()
                        .map(organization -> new ProfessionalOrganizationContextResult(
                                organization.organizationId(),
                                organization.displayName(),
                                organization.capabilities()))
                        .toList());
    }
}
