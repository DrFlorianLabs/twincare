package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.TokenDigest;

public interface PatientAccessSessionRepository {

    void add(PatientAccessSession session);

    Optional<PatientAccessSessionScope> findScopeByTokenDigest(TokenDigest tokenDigest);

    Optional<PatientAccessSession> findByIdForUpdate(
            UUID id,
            PatientInvitationId invitationId,
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    void update(PatientAccessSession session);

    void revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt);
}
