package backend.preparation.application.port.in;

import java.util.List;
import java.util.Objects;

public record ProfessionalPreparationPageResult(
        List<ProfessionalPreparationListItemResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ProfessionalPreparationPageResult {
        content = List.copyOf(Objects.requireNonNull(content, "Preparation page content is required"));
    }
}
