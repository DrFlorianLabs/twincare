package backend.preparation.application.port.in;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationTaskDescription;

public record CreatePreparationTaskCommand(
        OrganizationId organizationId,
        PreparationCaseId caseId,
        UUID requestId,
        PreparationTaskDescription description,
        Optional<Long> assigneeUserId) {

    public CreatePreparationTaskCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(caseId, "Preparation case identifier is required");
        Objects.requireNonNull(requestId, "Creation request identifier is required");
        Objects.requireNonNull(description, "Preparation task description is required");
        Objects.requireNonNull(assigneeUserId, "Assignee container is required");
        assigneeUserId.ifPresent(value -> {
            if (value <= 0) {
                throw new IllegalArgumentException("Preparation task assignee is invalid");
            }
        });
    }
}
