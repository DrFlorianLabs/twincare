package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record ProfessionalPreparationViewResponse(
        UUID caseId,
        String patientReference,
        String appointmentReference,
        Instant scheduledAt,
        String caseStatus,
        PreparationReadinessResponse readiness,
        ProfessionalPatientReasonResponse patientReason,
        ProfessionalAttachmentResponse attachment,
        List<PreparationTaskResponse> tasks,
        ProfessionalReadyConfirmationResponse readyConfirmation,
        int openTaskCount,
        long revision,
        Instant createdAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "ProfessionalPreparationViewResponse[clinical data redacted]";
    }
}
