package backend.preparation.application.port.in;

public interface GetPatientReasonUseCase {

    PatientReasonResult get(GetPatientReasonCommand command);
}
