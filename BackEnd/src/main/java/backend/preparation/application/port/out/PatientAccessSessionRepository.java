package backend.preparation.application.port.out;

import java.time.Instant;

import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitationId;

public interface PatientAccessSessionRepository {

    void add(PatientAccessSession session);

    void revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt);
}
