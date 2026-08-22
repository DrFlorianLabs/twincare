package backend.preparation.application.port.out;

import java.util.Optional;

public interface PatientReasonReader {

    Optional<PatientReasonSnapshot> findForActivePatientSession(PatientReasonLookup lookup);
}
