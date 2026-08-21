package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public record DownloadProfessionalAttachmentCommand(
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId) {

    public DownloadProfessionalAttachmentCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
    }

    @Override
    public String toString() {
        return "DownloadProfessionalAttachmentCommand[scope redacted]";
    }
}
