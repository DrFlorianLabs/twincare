package backend.preparation.application.port.in;

import java.util.Objects;
import java.util.OptionalLong;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public record ListPreparationAuditEventsCommand(
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId,
        OptionalLong beforeSequence,
        int limit) {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 50;

    public ListPreparationAuditEventsCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(preparationCaseId, "Preparation case identifier is required");
        Objects.requireNonNull(beforeSequence, "Audit cursor container is required");
        if (beforeSequence.isPresent() && beforeSequence.getAsLong() <= 0) {
            throw new IllegalArgumentException("Audit cursor must be positive");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Audit page size is invalid");
        }
    }
}
