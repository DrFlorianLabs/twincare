package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record ProfessionalPreparationListItemResponse(
        UUID caseId,
        String patientReference,
        String appointmentReference,
        Instant scheduledAt,
        String caseStatus,
        PreparationReadinessResponse readiness,
        int openTaskCount,
        boolean attachmentPresent,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "ProfessionalPreparationListItemResponse[clinical data redacted]";
    }
}
