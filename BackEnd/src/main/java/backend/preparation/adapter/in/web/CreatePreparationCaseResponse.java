package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record CreatePreparationCaseResponse(
        UUID caseId,
        String status,
        Instant createdAt) {
}
