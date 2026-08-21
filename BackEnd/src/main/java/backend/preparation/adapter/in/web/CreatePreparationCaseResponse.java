package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record CreatePreparationCaseResponse(
        UUID id,
        String status,
        Instant createdAt) {
}
