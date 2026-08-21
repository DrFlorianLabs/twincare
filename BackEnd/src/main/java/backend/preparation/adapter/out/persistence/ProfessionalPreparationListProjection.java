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

    Boolean getAttachmentPresent();

    Instant getUpdatedAt();
}
