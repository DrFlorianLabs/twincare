package backend.clinicalcontext.domain;

import java.util.Objects;
import java.util.UUID;

public record ClinicalFactId(UUID value) {

    public ClinicalFactId {
        Objects.requireNonNull(value, "Clinical fact identifier is required");
    }
}
