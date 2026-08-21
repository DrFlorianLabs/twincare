package backend.preparation.adapter.in.web;

import java.util.Objects;

import backend.preparation.application.port.in.PatientAccessGrant;

public record PatientPreparationPrincipal(PatientAccessGrant grant) {

    public static final String AUTHORITY = "PREPARATION_CASE_CONTRIBUTE";

    public PatientPreparationPrincipal {
        Objects.requireNonNull(grant, "Authenticated patient access grant is required");
    }

    @Override
    public String toString() {
        return "PatientPreparationPrincipal[technical scope redacted]";
    }
}
