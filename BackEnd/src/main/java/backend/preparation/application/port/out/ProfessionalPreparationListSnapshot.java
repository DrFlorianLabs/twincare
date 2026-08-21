package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;

public record ProfessionalPreparationListSnapshot(
        PreparationCaseId caseId,
        PatientReference patientReference,
        Optional<AppointmentReference> appointmentReference,
        Optional<Instant> scheduledAt,
        PreparationCaseStatus caseStatus,
        boolean patientReasonPresent,
        Optional<Instant> patientReasonRecordedAt,
        int openTaskCount,
        Optional<ProfessionalReadyConfirmationSnapshot> readyConfirmation,
        boolean attachmentPresent,
        long revision,
        Instant updatedAt) {

    public ProfessionalPreparationListSnapshot {
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(patientReference, "Patient reference is required");
        Objects.requireNonNull(appointmentReference, "Appointment reference container is required");
        Objects.requireNonNull(scheduledAt, "Scheduled timestamp container is required");
        Objects.requireNonNull(caseStatus, "Preparation case status is required");
        Objects.requireNonNull(patientReasonRecordedAt, "Patient reason timestamp container is required");
        if (patientReasonPresent != patientReasonRecordedAt.isPresent()) {
            throw new IllegalArgumentException("Patient reason evidence is inconsistent");
        }
        if (openTaskCount < 0 || revision < 0) {
            throw new IllegalArgumentException("Preparation case evidence is invalid");
        }
        Objects.requireNonNull(readyConfirmation, "Readiness confirmation container is required");
        Objects.requireNonNull(updatedAt, "Preparation case update timestamp is required");
    }
}
