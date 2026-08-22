package backend.preparation.application.service;

import java.time.Clock;
import java.util.Objects;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.GetPatientReasonCommand;
import backend.preparation.application.port.in.GetPatientReasonUseCase;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.PatientReasonResult;
import backend.preparation.application.port.out.PatientReasonLookup;
import backend.preparation.application.port.out.PatientReasonReader;

public final class GetPatientReasonService implements GetPatientReasonUseCase {

    private final PatientReasonReader patientReasons;
    private final Clock clock;

    public GetPatientReasonService(PatientReasonReader patientReasons, Clock clock) {
        this.patientReasons = Objects.requireNonNull(patientReasons);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PatientReasonResult get(GetPatientReasonCommand command) {
        PatientAccessGrant grant = requireGrant(command == null ? null : command.grant());
        var lookup = new PatientReasonLookup(
                grant.sessionId(),
                grant.invitationId(),
                grant.organizationId(),
                grant.preparationCaseId(),
                grant.expiresAt(),
                clock.instant());
        var snapshot = patientReasons.findForActivePatientSession(lookup)
                .orElseThrow(PatientSessionUnavailableException::new);
        return new PatientReasonResult(snapshot.reason());
    }

    private PatientAccessGrant requireGrant(PatientAccessGrant grant) {
        if (grant == null) {
            throw new PatientSessionUnavailableException();
        }
        return grant;
    }
}
