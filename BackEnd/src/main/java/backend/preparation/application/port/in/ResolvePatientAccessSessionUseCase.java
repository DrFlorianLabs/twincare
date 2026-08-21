package backend.preparation.application.port.in;

public interface ResolvePatientAccessSessionUseCase {

    PatientAccessGrant resolve(ResolvePatientAccessSessionCommand command);
}
