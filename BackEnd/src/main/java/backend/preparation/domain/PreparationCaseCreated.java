package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;

public record PreparationCaseCreated(
        PreparationCaseId caseId,
        OrganizationId organizationId,
        Instant occurredAt) implements PreparationCaseEvent {

    public PreparationCaseCreated {
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(occurredAt, "Event timestamp is required");
    }
}
