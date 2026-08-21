package backend.preparation.domain;

import java.time.Instant;

public sealed interface PreparationCaseEvent permits PreparationCaseCreated,
        PreparationCaseStatusChanged, PatientReasonRecorded {

    PreparationCaseId caseId();

    OrganizationId organizationId();

    Instant occurredAt();
}
