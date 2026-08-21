package backend.preparation.adapter.in.web;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePreparationCaseRequest(
        @NotBlank @Size(max = 128) String patientReference,
        @Size(max = 128) String appointmentReference,
        Instant scheduledAt) {
}
