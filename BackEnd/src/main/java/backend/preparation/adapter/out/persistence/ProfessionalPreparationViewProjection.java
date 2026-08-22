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

    Long getOpenTaskCount();

    Long getConfirmedByUserId();

    Instant getConfirmedAt();

    String getConfirmationRuleSetId();

    Integer getConfirmationRuleSetVersion();

    Long getReviewedCaseRevision();

    Instant getReviewedReasonRecordedAt();

    Integer getConfirmationOpenTaskCount();

    Long getClosedByUserId();

    Instant getClosedAt();

    Long getReadyCaseRevision();

    Long getClosedCaseRevision();

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

    Long getRevision();

    Instant getUpdatedAt();
}
