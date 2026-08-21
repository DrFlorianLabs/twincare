package backend.preparation.domain;

import java.util.Objects;
import java.util.UUID;

public record PatientInvitationId(UUID value) {

    public PatientInvitationId {
        Objects.requireNonNull(value, "Patient invitation identifier is required");
    }
}
