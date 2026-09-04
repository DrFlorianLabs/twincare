package backend.clinicalcontext.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import backend.clinicalcontext.domain.ClinicalFactKind;
import backend.clinicalcontext.domain.FactLifecycleStatus;
import jakarta.persistence.LockModeType;

interface SpringDataClinicalFactRepository extends JpaRepository<ClinicalFactJpaEntity, UUID> {

    Optional<ClinicalFactJpaEntity> findByContextIdAndOwnerIdAndKindAndLifecycleStatus(
            UUID contextId,
            String ownerId,
            ClinicalFactKind kind,
            FactLifecycleStatus lifecycleStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select fact
            from ClinicalFactJpaEntity fact
            where fact.id = :factId
              and fact.contextId = :contextId
              and fact.ownerId = :ownerId
            """)
    Optional<ClinicalFactJpaEntity> findByIdentityForUpdate(
            @Param("factId") UUID factId,
            @Param("contextId") UUID contextId,
            @Param("ownerId") String ownerId);

    @Query("""
            select fact
            from ClinicalFactJpaEntity fact
            where fact.contextId = :contextId
              and fact.ownerId = :ownerId
            order by fact.recordedAt asc, fact.id asc
            """)
    List<ClinicalFactJpaEntity> findAllByContext(
            @Param("contextId") UUID contextId,
            @Param("ownerId") String ownerId);
}
