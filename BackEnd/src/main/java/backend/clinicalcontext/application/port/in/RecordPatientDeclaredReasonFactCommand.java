package backend.clinicalcontext.application.port.in;

import java.time.Instant;
import java.util.Objects;

import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ConsultationReasonFact;

public record RecordPatientDeclaredReasonFactCommand(
        ClinicalContextId contextId,
        ClinicalContextOwner owner,
        String text,
        Instant recordedAt) {

    public RecordPatientDeclaredReasonFactCommand {
        Objects.requireNonNull(contextId, "Clinical context identifier is required");
        Objects.requireNonNull(owner, "Clinical context owner is required");
        Objects.requireNonNull(recordedAt, "Fact recording timestamp is required");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Patient-declared reason is required");
        }
        text = text.trim();
        if (text.isEmpty()
                || text.indexOf('\0') >= 0
                || text.length() > ConsultationReasonFact.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Patient-declared reason is invalid");
        }
    }

    @Override
    public String toString() {
        return "RecordPatientDeclaredReasonFactCommand[contextId=" + contextId
                + ", owner=" + owner + ", text=<redacted>, recordedAt=" + recordedAt + "]";
    }
}
