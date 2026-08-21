package backend.preparation.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import backend.preparation.domain.PreparationTaskStatus;
import jakarta.persistence.LockModeType;

interface SpringDataPreparationTaskRepository extends JpaRepository<PreparationTaskJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from PreparationTaskJpaEntity task
            where task.id = :taskId
              and task.preparationCaseId = :caseId
              and task.organizationId = :organizationId
            """)
    Optional<PreparationTaskJpaEntity> findForUpdate(
            @Param("taskId") UUID taskId,
            @Param("caseId") UUID caseId,
            @Param("organizationId") String organizationId);

    Optional<PreparationTaskJpaEntity> findByCreationRequestIdAndPreparationCaseIdAndOrganizationId(
            UUID requestId,
            UUID caseId,
            String organizationId);

    long countByPreparationCaseIdAndOrganizationId(UUID caseId, String organizationId);

    long countByPreparationCaseIdAndOrganizationIdAndStatus(
            UUID caseId,
            String organizationId,
            PreparationTaskStatus status);

    List<PreparationTaskJpaEntity> findAllByPreparationCaseIdAndOrganizationIdOrderByCreatedAtAscIdAsc(
            UUID caseId,
            String organizationId);
}
