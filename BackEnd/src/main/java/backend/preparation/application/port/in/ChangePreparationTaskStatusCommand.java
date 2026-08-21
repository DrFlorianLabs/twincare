package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationTaskId;
import backend.preparation.domain.PreparationTaskStatus;

public record ChangePreparationTaskStatusCommand(
        OrganizationId organizationId,
        PreparationCaseId caseId,
        PreparationTaskId taskId,
        PreparationTaskStatus targetStatus) {

    public ChangePreparationTaskStatusCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(taskId, "Preparation task identifier is required");
        if (targetStatus == null || !targetStatus.isTerminal()) {
            throw new IllegalArgumentException("Preparation task target status is invalid");
        }
    }
}
