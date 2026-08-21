package backend.preparation.adapter.in.web;

import java.util.List;

public record ProfessionalPreparationPageResponse(
        List<ProfessionalPreparationListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    @Override
    public String toString() {
        return "ProfessionalPreparationPageResponse[clinical data redacted]";
    }
}
