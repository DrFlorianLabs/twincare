package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record PreparationTaskResponse(
        UUID id,
        String label,
        long assigneeUserId,
        String status,
        long createdByUserId,
        Instant createdAt,
        Long closedByUserId,
        Instant closedAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "PreparationTaskResponse[id=" + id + ", label=redacted, status=" + status + "]";
    }
}
