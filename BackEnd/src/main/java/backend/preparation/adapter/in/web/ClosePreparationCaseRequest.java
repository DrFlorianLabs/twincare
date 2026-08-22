package backend.preparation.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClosePreparationCaseRequest(
        @NotNull @Positive Long expectedRevision) {
}
