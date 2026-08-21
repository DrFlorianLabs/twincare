package backend.preparation.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ConfirmPreparationReadyRequest(
        @NotNull @PositiveOrZero Long expectedRevision) {
}
