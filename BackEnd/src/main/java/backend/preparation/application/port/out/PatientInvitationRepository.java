package backend.preparation.application.port.out;

import java.util.Optional;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.TokenDigest;

public interface PatientInvitationRepository {

    Optional<PatientInvitation> findActiveForUpdate(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    Optional<PatientInvitation> findByIdForUpdate(
            PatientInvitationId invitationId,
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId);

    Optional<PatientInvitation> findByTokenDigestForUpdate(TokenDigest tokenDigest);

    Optional<PatientInvitationScope> findScopeByTokenDigest(TokenDigest tokenDigest);

    void add(PatientInvitation invitation);

    void update(PatientInvitation invitation);
}
