package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;

public record PatientReasonRecorded(
        PreparationCaseId caseId,
        OrganizationId organizationId,
        ProvenanceType provenance,
        Instant occurredAt) implements PreparationCaseEvent {

    public PatientReasonRecorded {
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(provenance, "Provenance is required");
        Objects.requireNonNull(occurredAt, "Event timestamp is required");
    }
}
