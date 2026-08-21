package backend.preparation.adapter.in.web;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import backend.preparation.domain.PatientStatement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientReasonRequest(
        @NotBlank
        @Size(max = PatientStatement.MAX_LENGTH)
        @JsonDeserialize(using = StrictJsonStringDeserializer.class)
        String reason) {

    @Override
    public String toString() {
        return "PatientReasonRequest[reason=<redacted>]";
    }
}
