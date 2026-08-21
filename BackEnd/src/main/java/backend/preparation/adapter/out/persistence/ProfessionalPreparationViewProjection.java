package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.ProvenanceType;

interface ProfessionalPreparationViewProjection {

    UUID getCaseId();

    String getPatientReference();

    String getAppointmentReference();

    Instant getScheduledAt();

    PreparationCaseStatus getCaseStatus();

    String getPatientReason();

    ProvenanceType getPatientReasonProvenance();

    Instant getPatientReasonRecordedAt();

    UUID getAttachmentId();

    String getAttachmentDisplayFilename();

    AttachmentMediaType getAttachmentMediaType();

    Long getAttachmentSizeBytes();

    Instant getAttachmentUploadedAt();

    Instant getAttachmentUpdatedAt();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}
