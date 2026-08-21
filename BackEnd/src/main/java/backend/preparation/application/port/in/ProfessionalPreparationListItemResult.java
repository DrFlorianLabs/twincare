package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadinessAssessment;

public record ProfessionalPreparationListItemResult(
        PreparationCaseId caseId,
        PatientReference patientReference,
        Optional<AppointmentReference> appointmentReference,
        Optional<Instant> scheduledAt,
        PreparationCaseStatus caseStatus,
        PreparationReadinessAssessment readiness,
        int openTaskCount,
        boolean attachmentPresent,
        Instant updatedAt) {

    public ProfessionalPreparationListItemResult {
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(patientReference, "Patient reference is required");
        Objects.requireNonNull(appointmentReference, "Appointment reference container is required");
        Objects.requireNonNull(scheduledAt, "Scheduled timestamp container is required");
        Objects.requireNonNull(caseStatus, "Preparation case status is required");
        Objects.requireNonNull(readiness, "Preparation readiness is required");
        if (openTaskCount < 0) {
            throw new IllegalArgumentException("Open task count cannot be negative");
        }
        Objects.requireNonNull(updatedAt, "Preparation case update timestamp is required");
    }
}
