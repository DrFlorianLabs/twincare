package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadinessAssessment;

public record ProfessionalPreparationViewResult(
        PreparationCaseId caseId,
        PatientReference patientReference,
        Optional<AppointmentReference> appointmentReference,
        Optional<Instant> scheduledAt,
        PreparationCaseStatus caseStatus,
        PreparationReadinessAssessment readiness,
        Optional<PatientStatement> patientReason,
        Optional<ProfessionalAttachmentResult> attachment,
        List<PreparationTaskResult> tasks,
        Optional<ProfessionalReadyConfirmationResult> readyConfirmation,
        int openTaskCount,
        long revision,
        Instant createdAt,
        Instant updatedAt) {

    public ProfessionalPreparationViewResult {
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(patientReference, "Patient reference is required");
        Objects.requireNonNull(appointmentReference, "Appointment reference container is required");
        Objects.requireNonNull(scheduledAt, "Scheduled timestamp container is required");
        Objects.requireNonNull(caseStatus, "Preparation case status is required");
        Objects.requireNonNull(readiness, "Preparation readiness is required");
        Objects.requireNonNull(patientReason, "Patient reason container is required");
        Objects.requireNonNull(attachment, "Attachment container is required");
        tasks = List.copyOf(Objects.requireNonNull(tasks, "Preparation task list is required"));
        Objects.requireNonNull(readyConfirmation, "Readiness confirmation container is required");
        if (openTaskCount < 0 || revision < 0) {
            throw new IllegalArgumentException("Preparation case evidence is invalid");
        }
        Objects.requireNonNull(createdAt, "Preparation case creation timestamp is required");
        Objects.requireNonNull(updatedAt, "Preparation case update timestamp is required");
    }
}
