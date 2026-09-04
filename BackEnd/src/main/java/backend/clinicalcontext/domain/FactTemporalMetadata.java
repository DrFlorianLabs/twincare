package backend.clinicalcontext.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record FactTemporalMetadata(
        Instant recordedAt,
        Optional<Instant> effectiveFrom,
        Optional<Instant> effectiveTo) {

    public FactTemporalMetadata {
        Objects.requireNonNull(recordedAt, "Fact recording timestamp is required");
        Objects.requireNonNull(effectiveFrom, "Effective-from container is required");
        Objects.requireNonNull(effectiveTo, "Effective-to container is required");
        if (effectiveFrom.isEmpty() && effectiveTo.isPresent()) {
            throw new IllegalArgumentException("Effective-to requires an effective-from timestamp");
        }
        if (effectiveFrom.isPresent()
                && effectiveTo.filter(end -> end.isBefore(effectiveFrom.orElseThrow())).isPresent()) {
            throw new IllegalArgumentException("Fact effective period is invalid");
        }
    }

    public static FactTemporalMetadata recordedAtOnly(Instant recordedAt) {
        return new FactTemporalMetadata(recordedAt, Optional.empty(), Optional.empty());
    }
}
