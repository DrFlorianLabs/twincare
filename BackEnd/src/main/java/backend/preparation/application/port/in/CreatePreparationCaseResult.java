package backend.preparation.application.port.in;

import java.time.Instant;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;

public record CreatePreparationCaseResult(
        PreparationCaseId id,
        OrganizationId organizationId,
        PreparationCaseStatus status,
        Instant createdAt) {
}
