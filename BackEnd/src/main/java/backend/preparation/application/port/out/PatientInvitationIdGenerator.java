package backend.preparation.application.port.out;

import backend.preparation.domain.PatientInvitationId;

public interface PatientInvitationIdGenerator {

    PatientInvitationId nextId();
}
