package backend.preparation.adapter.out.persistence;

import java.util.Optional;
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
                           case when attachment.id is null then false else true end as attachmentPresent,
                           preparationCase.updatedAt as updatedAt
                    from PreparationCaseJpaEntity preparationCase
                    left join PreparationAttachmentJpaEntity attachment
                      on attachment.preparationCaseId = preparationCase.id
                     and attachment.organizationId = preparationCase.organizationId
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
                   preparationCase.updatedAt as updatedAt
            from PreparationCaseJpaEntity preparationCase
            left join PreparationAttachmentJpaEntity attachment
              on attachment.preparationCaseId = preparationCase.id
             and attachment.organizationId = preparationCase.organizationId
            where preparationCase.organizationId = :organizationId
              and preparationCase.id = :caseId
            """)
    Optional<ProfessionalPreparationViewProjection> findView(
            @Param("organizationId") String organizationId,
            @Param("caseId") UUID caseId);
}
