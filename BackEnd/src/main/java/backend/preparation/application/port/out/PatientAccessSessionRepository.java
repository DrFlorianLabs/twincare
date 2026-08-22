package backend.preparation.application.port.out;

import java.time.Instant;
import java.util.List;
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

    Optional<PatientAccessSessionScope> findScopeByIdentity(
            UUID id,
            PatientInvitationId invitationId,
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    Optional<PatientAccessSession> findByIdForUpdate(
            UUID id,
            PatientInvitationId invitationId,
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    List<PatientAccessSession> findAllByCaseForUpdate(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    void update(PatientAccessSession session);

    boolean revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt);
}
