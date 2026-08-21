package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;

public record ProfessionalPreparationViewSnapshot(
        PreparationCaseId caseId,
        PatientReference patientReference,
        Optional<AppointmentReference> appointmentReference,
        Optional<Instant> scheduledAt,
        PreparationCaseStatus caseStatus,
        Optional<PatientStatement> patientReason,
        Optional<ProfessionalAttachmentSnapshot> attachment,
        Instant createdAt,
        Instant updatedAt) {

    public ProfessionalPreparationViewSnapshot {
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(patientReference, "Patient reference is required");
        Objects.requireNonNull(appointmentReference, "Appointment reference container is required");
        Objects.requireNonNull(scheduledAt, "Scheduled timestamp container is required");
        Objects.requireNonNull(caseStatus, "Preparation case status is required");
        Objects.requireNonNull(patientReason, "Patient reason container is required");
        Objects.requireNonNull(attachment, "Attachment container is required");
        Objects.requireNonNull(createdAt, "Preparation case creation timestamp is required");
        Objects.requireNonNull(updatedAt, "Preparation case update timestamp is required");
    }
}
