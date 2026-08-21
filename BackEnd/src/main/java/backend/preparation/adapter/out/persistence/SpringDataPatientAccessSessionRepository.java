package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface SpringDataPatientAccessSessionRepository extends JpaRepository<PatientAccessSessionJpaEntity, UUID> {

    @Query("""
            select session.id as sessionId,
                   session.invitationId as invitationId,
                   session.organizationId as organizationId,
                   session.preparationCaseId as preparationCaseId,
                   session.scope as scope,
                   invitation.status as invitationStatus,
                   preparationCase.status as preparationCaseStatus,
                   session.issuedAt as issuedAt,
                   session.expiresAt as expiresAt,
                   session.revokedAt as revokedAt
            from PatientAccessSessionJpaEntity session,
                 PatientInvitationJpaEntity invitation,
                 PreparationCaseJpaEntity preparationCase
            where session.tokenKeyId = :tokenKeyId
              and session.tokenDigest = :tokenDigest
              and invitation.id = session.invitationId
              and invitation.organizationId = session.organizationId
              and invitation.preparationCaseId = session.preparationCaseId
              and preparationCase.id = session.preparationCaseId
              and preparationCase.organizationId = session.organizationId
            """)
    Optional<PatientAccessSessionScopeView> findScopeByTokenDigest(
            @Param("tokenKeyId") String tokenKeyId,
            @Param("tokenDigest") String tokenDigest);

    @Query("""
            select session.id as sessionId,
                   session.invitationId as invitationId,
                   session.organizationId as organizationId,
                   session.preparationCaseId as preparationCaseId,
                   session.scope as scope,
                   invitation.status as invitationStatus,
                   preparationCase.status as preparationCaseStatus,
                   session.issuedAt as issuedAt,
                   session.expiresAt as expiresAt,
                   session.revokedAt as revokedAt
            from PatientAccessSessionJpaEntity session,
                 PatientInvitationJpaEntity invitation,
                 PreparationCaseJpaEntity preparationCase
            where session.id = :sessionId
              and session.invitationId = :invitationId
              and session.preparationCaseId = :preparationCaseId
              and session.organizationId = :organizationId
              and invitation.id = session.invitationId
              and invitation.organizationId = session.organizationId
              and invitation.preparationCaseId = session.preparationCaseId
              and preparationCase.id = session.preparationCaseId
              and preparationCase.organizationId = session.organizationId
            """)
    Optional<PatientAccessSessionScopeView> findScopeByIdentity(
            @Param("sessionId") UUID sessionId,
            @Param("invitationId") UUID invitationId,
            @Param("preparationCaseId") UUID preparationCaseId,
            @Param("organizationId") String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientAccessSessionJpaEntity> findByIdAndInvitationIdAndPreparationCaseIdAndOrganizationId(
            UUID id,
            UUID invitationId,
            UUID preparationCaseId,
            String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientAccessSessionJpaEntity> findByInvitationId(UUID invitationId);

    interface PatientAccessSessionScopeView {
        UUID getSessionId();

        UUID getInvitationId();

        String getOrganizationId();

        UUID getPreparationCaseId();

        String getScope();

        backend.preparation.domain.PatientInvitationStatus getInvitationStatus();

        backend.preparation.domain.PreparationCaseStatus getPreparationCaseStatus();

        java.time.Instant getIssuedAt();

        java.time.Instant getExpiresAt();

        java.time.Instant getRevokedAt();
    }
}
