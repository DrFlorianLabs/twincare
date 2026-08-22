package backend.preparation.application.port.in;

public record GetPatientReasonCommand(PatientAccessGrant grant) {

    @Override
    public String toString() {
        return "GetPatientReasonCommand[patient access grant redacted]";
    }
}
