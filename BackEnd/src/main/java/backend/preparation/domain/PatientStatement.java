package backend.preparation.domain;

import java.time.Instant;
import java.util.Objects;

public record PatientStatement(String text, ProvenanceType provenance, Instant recordedAt) {

    public static final int MAX_LENGTH = 4_000;

    public PatientStatement {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Patient statement is required");
        }
        text = text.trim();
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Patient statement exceeds the allowed length");
        }
        Objects.requireNonNull(provenance, "Patient statement provenance is required");
        Objects.requireNonNull(recordedAt, "Patient statement timestamp is required");
    }

    public static PatientStatement declaredByPatient(String text, Instant recordedAt) {
        return new PatientStatement(text, ProvenanceType.PATIENT_DECLARED, recordedAt);
    }

    @Override
    public String toString() {
        return "PatientStatement[text=<redacted>, provenance=" + provenance
                + ", recordedAt=" + recordedAt + "]";
    }
}
