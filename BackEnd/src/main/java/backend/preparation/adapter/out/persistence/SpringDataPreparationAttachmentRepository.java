package backend.preparation.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface SpringDataPreparationAttachmentRepository
        extends JpaRepository<PreparationAttachmentJpaEntity, UUID> {

    Optional<PreparationAttachmentJpaEntity> findByPreparationCaseIdAndOrganizationId(
            UUID preparationCaseId,
            String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attachment
            from PreparationAttachmentJpaEntity attachment
            where attachment.preparationCaseId = :preparationCaseId
              and attachment.organizationId = :organizationId
            """)
    Optional<PreparationAttachmentJpaEntity> findByCaseForUpdate(
            @Param("preparationCaseId") UUID preparationCaseId,
            @Param("organizationId") String organizationId);

    boolean existsByStorageKey(UUID storageKey);

    @Query("select attachment.storageKey from PreparationAttachmentJpaEntity attachment")
    List<UUID> findAllStorageKeys();
}
