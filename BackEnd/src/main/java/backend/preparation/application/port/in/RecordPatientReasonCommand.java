package backend.preparation.application.port.in;

import backend.preparation.application.InvalidPatientReasonCommandException;
import backend.preparation.domain.PatientStatement;

public record RecordPatientReasonCommand(PatientAccessGrant grant, String reason) {

    public RecordPatientReasonCommand {
        if (reason == null || reason.isBlank()) {
            throw new InvalidPatientReasonCommandException();
        }
        reason = reason.trim();
        if (reason.isEmpty()
                || reason.indexOf('\0') >= 0
                || reason.length() > PatientStatement.MAX_LENGTH) {
            throw new InvalidPatientReasonCommandException();
        }
    }

    @Override
    public String toString() {
        return "RecordPatientReasonCommand[patient access grant and reason redacted]";
    }
}
