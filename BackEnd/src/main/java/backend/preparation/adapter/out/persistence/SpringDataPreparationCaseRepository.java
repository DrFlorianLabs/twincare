package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPreparationCaseRepository extends JpaRepository<PreparationCaseJpaEntity, UUID> {

    Optional<PreparationCaseJpaEntity> findByIdAndOrganizationId(UUID id, String organizationId);

    boolean existsByOrganizationIdAndAppointmentReference(String organizationId, String appointmentReference);

    boolean existsByOrganizationIdAndPatientReferenceAndScheduledAt(
            String organizationId,
            String patientReference,
            Instant scheduledAt);
}
