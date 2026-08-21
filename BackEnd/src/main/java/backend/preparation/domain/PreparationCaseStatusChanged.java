package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;

public record PreparationCaseStatusChanged(
        PreparationCaseId caseId,
        OrganizationId organizationId,
        PreparationCaseStatus previousStatus,
        PreparationCaseStatus newStatus,
        Instant occurredAt) implements PreparationCaseEvent {

    public PreparationCaseStatusChanged {
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(previousStatus, "Previous status is required");
        Objects.requireNonNull(newStatus, "New status is required");
        Objects.requireNonNull(occurredAt, "Event timestamp is required");
    }
}
