package backend.preparation.adapter.in.web;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatientReasonResponse(
        boolean present,
        String reason,
        Instant recordedAt) {

    public PatientReasonResponse {
        boolean hasCompleteReason = reason != null && recordedAt != null;
        boolean hasAnyReasonField = reason != null || recordedAt != null;
        if ((present && !hasCompleteReason) || (!present && hasAnyReasonField)) {
            throw new IllegalArgumentException("Patient reason response is inconsistent");
        }
    }

    public static PatientReasonResponse absent() {
        return new PatientReasonResponse(false, null, null);
    }

    public static PatientReasonResponse present(String reason, Instant recordedAt) {
        return new PatientReasonResponse(
                true,
                Objects.requireNonNull(reason, "Patient reason is required"),
                Objects.requireNonNull(recordedAt, "Patient reason timestamp is required"));
    }

    @Override
    public String toString() {
        return "PatientReasonResponse[reason=<redacted>]";
    }
}
