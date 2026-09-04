package backend.clinicalcontext.domain;

import java.util.Objects;
import java.util.UUID;

public record ClinicalContextId(UUID value) {

    public ClinicalContextId {
        Objects.requireNonNull(value, "Clinical context identifier is required");
    }
}
