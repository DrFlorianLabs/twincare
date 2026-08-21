package backend.preparation.application.port.out;

import java.util.List;
import java.util.Objects;

public record ProfessionalPreparationListPageSnapshot(
        List<ProfessionalPreparationListSnapshot> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ProfessionalPreparationListPageSnapshot {
        content = List.copyOf(Objects.requireNonNull(content, "Preparation page content is required"));
        if (page < 0 || size <= 0 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("Preparation page metadata is invalid");
        }
    }
}
