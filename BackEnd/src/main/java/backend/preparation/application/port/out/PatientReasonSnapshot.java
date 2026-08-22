package backend.preparation.application.port.out;

import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.ProvenanceType;

public record PatientReasonSnapshot(Optional<PatientStatement> reason) {

    public PatientReasonSnapshot {
        Objects.requireNonNull(reason, "Patient reason container is required");
        if (reason.filter(statement -> statement.provenance() != ProvenanceType.PATIENT_DECLARED).isPresent()) {
            throw new IllegalArgumentException("Patient reason provenance is invalid");
        }
    }

    @Override
    public String toString() {
        return "PatientReasonSnapshot[reason=<redacted>]";
    }
}
