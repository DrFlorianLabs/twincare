package backend.preparation.adapter.in.web;

import java.time.Instant;

public record ProfessionalPatientReasonResponse(
        boolean present,
        String text,
        String provenance,
        Instant recordedAt) {

    @Override
    public String toString() {
        return "ProfessionalPatientReasonResponse[text=<redacted>]";
    }
}
