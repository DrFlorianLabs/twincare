package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PatientReasonResponseTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T10:00:00Z");

    @Test
    void absentResponseRejectsEveryPartialClinicalValue() {
        assertThatThrownBy(() -> new PatientReasonResponse(false, "Motif synthétique", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientReasonResponse(false, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientReasonResponse(false, "Motif synthétique", RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void presentResponseRequiresBothClinicalValues() {
        assertThatThrownBy(() -> new PatientReasonResponse(true, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientReasonResponse(true, "Motif synthétique", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientReasonResponse(true, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
