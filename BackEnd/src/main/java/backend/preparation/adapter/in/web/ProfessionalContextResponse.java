package backend.preparation.adapter.in.web;

import java.util.List;

public record ProfessionalContextResponse(
        long actorUserId,
        String displayName,
        List<ProfessionalOrganizationContextResponse> organizations) {

    @Override
    public String toString() {
        return "ProfessionalContextResponse[actorUserId=" + actorUserId + ", identity redacted]";
    }
}
