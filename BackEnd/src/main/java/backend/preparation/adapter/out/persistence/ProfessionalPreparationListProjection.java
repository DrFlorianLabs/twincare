package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import backend.preparation.domain.PreparationCaseStatus;

interface ProfessionalPreparationListProjection {

    UUID getCaseId();

    String getPatientReference();

    String getAppointmentReference();

    Instant getScheduledAt();

    PreparationCaseStatus getCaseStatus();

    Boolean getPatientReasonPresent();

    Instant getPatientReasonRecordedAt();

    Long getOpenTaskCount();

    Long getConfirmedByUserId();

    Instant getConfirmedAt();

    String getConfirmationRuleSetId();

    Integer getConfirmationRuleSetVersion();

    Long getReviewedCaseRevision();

    Instant getReviewedReasonRecordedAt();

    Integer getConfirmationOpenTaskCount();

    Boolean getAttachmentPresent();

    Long getRevision();

    Instant getUpdatedAt();
}
