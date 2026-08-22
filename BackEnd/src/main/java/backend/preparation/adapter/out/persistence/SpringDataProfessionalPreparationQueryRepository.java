package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SpringDataProfessionalPreparationQueryRepository
        extends Repository<PreparationCaseJpaEntity, UUID> {

    @Query(
            value = """
                    select preparationCase.id as caseId,
                           preparationCase.patientReference as patientReference,
                           preparationCase.appointmentReference as appointmentReference,
                           preparationCase.scheduledAt as scheduledAt,
                           preparationCase.status as caseStatus,
                           case when preparationCase.patientReason is null then false else true end
                               as patientReasonPresent,
                           preparationCase.patientReasonRecordedAt as patientReasonRecordedAt,
                           (select count(task.id)
                            from PreparationTaskJpaEntity task
                            where task.preparationCaseId = preparationCase.id
                              and task.organizationId = preparationCase.organizationId
                              and task.status = backend.preparation.domain.PreparationTaskStatus.OPEN)
                               as openTaskCount,
                           confirmation.confirmedByUserId as confirmedByUserId,
                           confirmation.confirmedAt as confirmedAt,
                           confirmation.ruleSetId as confirmationRuleSetId,
                           confirmation.ruleSetVersion as confirmationRuleSetVersion,
                           confirmation.reviewedCaseRevision as reviewedCaseRevision,
                           confirmation.reviewedReasonRecordedAt as reviewedReasonRecordedAt,
                           confirmation.openTaskCount as confirmationOpenTaskCount,
                           closure.closedByUserId as closedByUserId,
                           closure.closedAt as closedAt,
                           closure.readyCaseRevision as readyCaseRevision,
                           closure.closedCaseRevision as closedCaseRevision,
                           case when attachment.id is null then false else true end as attachmentPresent,
                           preparationCase.version as revision,
                           preparationCase.updatedAt as updatedAt
                    from PreparationCaseJpaEntity preparationCase
                    left join PreparationAttachmentJpaEntity attachment
                      on attachment.preparationCaseId = preparationCase.id
                     and attachment.organizationId = preparationCase.organizationId
                    left join PreparationReadyConfirmationJpaEntity confirmation
                      on confirmation.preparationCaseId = preparationCase.id
                     and confirmation.organizationId = preparationCase.organizationId
                    left join PreparationCaseClosureJpaEntity closure
                      on closure.preparationCaseId = preparationCase.id
                     and closure.organizationId = preparationCase.organizationId
                    where preparationCase.organizationId = :organizationId
                    order by case when preparationCase.scheduledAt is null then 1 else 0 end,
                             preparationCase.scheduledAt asc,
                             preparationCase.updatedAt desc,
                             preparationCase.id asc
                    """,
            countQuery = """
                    select count(preparationCase.id)
                    from PreparationCaseJpaEntity preparationCase
                    where preparationCase.organizationId = :organizationId
                    """)
    Page<ProfessionalPreparationListProjection> findPageByOrganizationId(
            @Param("organizationId") String organizationId,
            Pageable pageable);

    @Query("""
            select preparationCase.id as caseId,
                   preparationCase.patientReference as patientReference,
                   preparationCase.appointmentReference as appointmentReference,
                   preparationCase.scheduledAt as scheduledAt,
                   preparationCase.status as caseStatus,
                   (select count(task.id)
                    from PreparationTaskJpaEntity task
                    where task.preparationCaseId = preparationCase.id
                      and task.organizationId = preparationCase.organizationId
                      and task.status = backend.preparation.domain.PreparationTaskStatus.OPEN)
                       as openTaskCount,
                   confirmation.confirmedByUserId as confirmedByUserId,
                   confirmation.confirmedAt as confirmedAt,
                   confirmation.ruleSetId as confirmationRuleSetId,
                   confirmation.ruleSetVersion as confirmationRuleSetVersion,
                   confirmation.reviewedCaseRevision as reviewedCaseRevision,
                   confirmation.reviewedReasonRecordedAt as reviewedReasonRecordedAt,
                   confirmation.openTaskCount as confirmationOpenTaskCount,
                   closure.closedByUserId as closedByUserId,
                   closure.closedAt as closedAt,
                   closure.readyCaseRevision as readyCaseRevision,
                   closure.closedCaseRevision as closedCaseRevision,
                   preparationCase.patientReason as patientReason,
                   preparationCase.patientReasonProvenance as patientReasonProvenance,
                   preparationCase.patientReasonRecordedAt as patientReasonRecordedAt,
                   attachment.id as attachmentId,
                   attachment.displayFilename as attachmentDisplayFilename,
                   attachment.mediaType as attachmentMediaType,
                   attachment.sizeBytes as attachmentSizeBytes,
                   attachment.uploadedAt as attachmentUploadedAt,
                   attachment.updatedAt as attachmentUpdatedAt,
                   preparationCase.createdAt as createdAt,
                   preparationCase.version as revision,
                   preparationCase.updatedAt as updatedAt
            from PreparationCaseJpaEntity preparationCase
            left join PreparationAttachmentJpaEntity attachment
              on attachment.preparationCaseId = preparationCase.id
             and attachment.organizationId = preparationCase.organizationId
            left join PreparationReadyConfirmationJpaEntity confirmation
              on confirmation.preparationCaseId = preparationCase.id
             and confirmation.organizationId = preparationCase.organizationId
            left join PreparationCaseClosureJpaEntity closure
              on closure.preparationCaseId = preparationCase.id
             and closure.organizationId = preparationCase.organizationId
            where preparationCase.organizationId = :organizationId
              and preparationCase.id = :caseId
            """)
    Optional<ProfessionalPreparationViewProjection> findView(
            @Param("organizationId") String organizationId,
            @Param("caseId") UUID caseId);

    @Query("""
            select task.id as taskId,
                   task.description as description,
                   task.assigneeUserId as assigneeUserId,
                   task.status as status,
                   task.createdByUserId as createdByUserId,
                   task.createdAt as createdAt,
                   task.closedByUserId as closedByUserId,
                   task.closedAt as closedAt,
                   task.updatedAt as updatedAt
            from PreparationTaskJpaEntity task
            where task.organizationId = :organizationId
              and task.preparationCaseId = :caseId
            order by case when task.status = backend.preparation.domain.PreparationTaskStatus.OPEN
                              then 0 else 1 end,
                     task.createdAt asc,
                     task.id asc
            """)
    List<ProfessionalPreparationTaskProjection> findTasks(
            @Param("organizationId") String organizationId,
            @Param("caseId") UUID caseId,
            Pageable pageable);
}
