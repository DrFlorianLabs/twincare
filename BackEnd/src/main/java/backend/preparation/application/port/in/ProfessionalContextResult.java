package backend.preparation.application.port.in;

import java.util.List;

public record ProfessionalContextResult(
        long actorUserId,
        String displayName,
        List<ProfessionalOrganizationContextResult> organizations) {

    public ProfessionalContextResult {
        if (actorUserId <= 0) {
            throw new IllegalArgumentException("Professional actor identifier is invalid");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Professional display name is required");
        }
        organizations = List.copyOf(organizations);
    }

    @Override
    public String toString() {
        return "ProfessionalContextResult[actorUserId=" + actorUserId + ", identity redacted]";
    }
}
