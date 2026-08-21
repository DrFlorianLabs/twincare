package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface SpringDataPatientInvitationRepository extends JpaRepository<PatientInvitationJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientInvitationJpaEntity> findByPreparationCaseIdAndOrganizationIdAndActiveSlot(
            UUID preparationCaseId,
            String organizationId,
            String activeSlot);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientInvitationJpaEntity> findByIdAndPreparationCaseIdAndOrganizationId(
            UUID id,
            UUID preparationCaseId,
            String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientInvitationJpaEntity> findByTokenKeyIdAndTokenDigest(
            String tokenKeyId,
            String tokenDigest);

    @Query("""
            select invitation.id as id,
                   invitation.organizationId as organizationId,
                   invitation.preparationCaseId as preparationCaseId
            from PatientInvitationJpaEntity invitation
            where invitation.tokenKeyId = :tokenKeyId
              and invitation.tokenDigest = :tokenDigest
            """)
    Optional<PatientInvitationScopeView> findScopeByTokenDigest(
            @Param("tokenKeyId") String tokenKeyId,
            @Param("tokenDigest") String tokenDigest);

    interface PatientInvitationScopeView {
        UUID getId();

        String getOrganizationId();

        UUID getPreparationCaseId();
    }
}
