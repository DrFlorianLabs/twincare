package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record ProfessionalPreparationViewResponse(
        UUID caseId,
        String patientReference,
        String appointmentReference,
        Instant scheduledAt,
        String caseStatus,
        PreparationReadinessResponse readiness,
        ProfessionalPatientReasonResponse patientReason,
        ProfessionalAttachmentResponse attachment,
        int openTaskCount,
        Instant createdAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "ProfessionalPreparationViewResponse[clinical data redacted]";
    }
}
