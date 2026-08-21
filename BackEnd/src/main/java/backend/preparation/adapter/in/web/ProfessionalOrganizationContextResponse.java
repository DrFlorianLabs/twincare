package backend.preparation.adapter.in.web;

import java.util.List;

public record ProfessionalOrganizationContextResponse(
        String organizationId,
        String displayName,
        List<String> capabilities) {

    @Override
    public String toString() {
        return "ProfessionalOrganizationContextResponse[organization data redacted]";
    }
}
