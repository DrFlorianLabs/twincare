package backend.preparation.application.port.in;

import java.util.Objects;

import backend.preparation.domain.OrganizationId;

public record ListProfessionalPreparationsCommand(
        OrganizationId organizationId,
        int page,
        int size) {

    public static final int MAX_PAGE_SIZE = 100;

    public ListProfessionalPreparationsCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Preparation page request is invalid");
        }
    }
}
