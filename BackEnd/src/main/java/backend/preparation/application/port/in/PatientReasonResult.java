package backend.preparation.application.port.in;

import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.PatientStatement;

public record PatientReasonResult(Optional<PatientStatement> reason) {

    public PatientReasonResult {
        Objects.requireNonNull(reason, "Patient reason container is required");
    }

    @Override
    public String toString() {
        return "PatientReasonResult[reason=<redacted>]";
    }
}
