package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePreparationCaseRequest(
        @NotNull UUID requestId,
        @NotBlank @Size(max = 128) String patientReference,
        @Size(max = 128) String appointmentReference,
        Instant scheduledAt) {

    @Override
    public String toString() {
        return "CreatePreparationCaseRequest[requestId=" + requestId + ", clinical input redacted]";
    }
}
