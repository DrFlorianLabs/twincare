package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface SpringDataPreparationCaseRepository extends JpaRepository<PreparationCaseJpaEntity, UUID> {

    Optional<PreparationCaseJpaEntity> findByIdAndOrganizationId(UUID id, String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select preparationCase
            from PreparationCaseJpaEntity preparationCase
            where preparationCase.id = :id
              and preparationCase.organizationId = :organizationId
            """)
    Optional<PreparationCaseJpaEntity> findByIdAndOrganizationIdForUpdate(
            @Param("id") UUID id,
            @Param("organizationId") String organizationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PreparationCaseJpaEntity preparationCase
            set preparationCase.version = preparationCase.version + 1
            where preparationCase.id = :id
              and preparationCase.organizationId = :organizationId
              and preparationCase.version = :expectedVersion
            """)
    int forceIncrementVersion(
            @Param("id") UUID id,
            @Param("organizationId") String organizationId,
            @Param("expectedVersion") long expectedVersion);

    boolean existsByOrganizationIdAndAppointmentReference(String organizationId, String appointmentReference);

    boolean existsByOrganizationIdAndPatientReferenceAndScheduledAt(
            String organizationId,
            String patientReference,
            Instant scheduledAt);
}
