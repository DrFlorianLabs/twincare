package backend.preparation.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import backend.preparation.domain.PreparationAuditEventType;

interface SpringDataPreparationAuditEventRepository
        extends JpaRepository<PreparationAuditEventJpaEntity, UUID> {

    @Query("""
            select coalesce(max(event.sequence), 0)
            from PreparationAuditEventJpaEntity event
            where event.organizationId = :organizationId
              and event.preparationCaseId = :caseId
            """)
    long findMaxSequence(
            @Param("organizationId") String organizationId,
            @Param("caseId") UUID caseId);

    @Query("""
            select event
            from PreparationAuditEventJpaEntity event
            where event.organizationId = :organizationId
              and event.preparationCaseId = :caseId
            order by event.sequence desc
            """)
    List<PreparationAuditEventJpaEntity> findLatest(
            @Param("organizationId") String organizationId,
            @Param("caseId") UUID caseId,
            Pageable pageable);

    @Query("""
            select event
            from PreparationAuditEventJpaEntity event
            where event.organizationId = :organizationId
              and event.preparationCaseId = :caseId
              and event.sequence < :beforeSequence
            order by event.sequence desc
            """)
    List<PreparationAuditEventJpaEntity> findBefore(
            @Param("organizationId") String organizationId,
            @Param("caseId") UUID caseId,
            @Param("beforeSequence") long beforeSequence,
            Pageable pageable);

    boolean existsByOrganizationIdAndPreparationCaseIdAndEventTypeAndSequence(
            String organizationId,
            UUID preparationCaseId,
            PreparationAuditEventType eventType,
            long sequence);
}
