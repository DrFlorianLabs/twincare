package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPreparationReadyConfirmationRepository
        extends JpaRepository<PreparationReadyConfirmationJpaEntity, UUID> {

    Optional<PreparationReadyConfirmationJpaEntity> findByPreparationCaseIdAndOrganizationId(
            UUID caseId,
            String organizationId);
}
