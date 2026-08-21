package backend.preparation.application.port.out;

import java.time.Instant;

public interface PatientInvitationExchangeGuard {

    void requireAttemptAllowed(Instant occurredAt);
}
