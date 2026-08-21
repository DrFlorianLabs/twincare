package backend.preparation.application.port.in;

import java.util.Objects;

public record EndPatientAccessSessionCommand(PatientAccessGrant grant) {

    public EndPatientAccessSessionCommand {
        Objects.requireNonNull(grant, "Authenticated patient access grant is required");
    }
}
