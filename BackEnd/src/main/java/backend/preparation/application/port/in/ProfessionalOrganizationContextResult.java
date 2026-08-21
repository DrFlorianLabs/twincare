package backend.preparation.application.port.in;

import java.util.List;

import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.domain.OrganizationId;

public record ProfessionalOrganizationContextResult(
        OrganizationId organizationId,
        String displayName,
        List<PreparationCaseCapability> capabilities) {

    public ProfessionalOrganizationContextResult {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization identifier is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Organization display name is required");
        }
        capabilities = List.copyOf(capabilities);
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("At least one clinical capability is required");
        }
    }
}
