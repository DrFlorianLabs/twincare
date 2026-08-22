package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
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

    @Query("""
            select preparationCase.patientReason as patientReason,
                   preparationCase.patientReasonRecordedAt as patientReasonRecordedAt
            from PatientAccessSessionJpaEntity session,
                 PatientInvitationJpaEntity invitation,
                 PreparationCaseJpaEntity preparationCase
            where session.id = :sessionId
              and session.invitationId = :invitationId
              and session.preparationCaseId = :preparationCaseId
              and session.organizationId = :organizationId
              and session.scope = :scope
              and session.issuedAt <= :readAt
              and session.expiresAt = :sessionExpiresAt
              and session.expiresAt > :readAt
              and session.revokedAt is null
              and invitation.id = session.invitationId
              and invitation.organizationId = session.organizationId
              and invitation.preparationCaseId = session.preparationCaseId
              and invitation.status = :invitationStatus
              and invitation.consumedAt = session.issuedAt
              and invitation.expiresAt = session.expiresAt
              and invitation.expiresAt > :readAt
              and preparationCase.id = session.preparationCaseId
              and preparationCase.organizationId = session.organizationId
              and preparationCase.status in :contributionStatuses
              and (
                    preparationCase.patientReason is null
                    or (
                        preparationCase.patientReasonProvenance = :patientDeclaredProvenance
                        and preparationCase.patientReasonRecordedAt <= :readAt
                    )
              )
            """)
    Optional<PatientReasonView> findReasonForActivePatientSession(
            @Param("sessionId") UUID sessionId,
            @Param("invitationId") UUID invitationId,
            @Param("preparationCaseId") UUID preparationCaseId,
            @Param("organizationId") String organizationId,
            @Param("sessionExpiresAt") Instant sessionExpiresAt,
            @Param("readAt") Instant readAt,
            @Param("scope") String scope,
            @Param("contributionStatuses") List<backend.preparation.domain.PreparationCaseStatus> contributionStatuses,
            @Param("invitationStatus") backend.preparation.domain.PatientInvitationStatus invitationStatus,
            @Param("patientDeclaredProvenance") backend.preparation.domain.ProvenanceType patientDeclaredProvenance);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientAccessSessionJpaEntity> findByIdAndInvitationIdAndPreparationCaseIdAndOrganizationId(
            UUID id,
            UUID invitationId,
            UUID preparationCaseId,
            String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientAccessSessionJpaEntity> findByInvitationId(UUID invitationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PatientAccessSessionJpaEntity> findAllByPreparationCaseIdAndOrganizationIdOrderById(
            UUID preparationCaseId,
            String organizationId);

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

    interface PatientReasonView {
        String getPatientReason();

        Instant getPatientReasonRecordedAt();
    }
}
