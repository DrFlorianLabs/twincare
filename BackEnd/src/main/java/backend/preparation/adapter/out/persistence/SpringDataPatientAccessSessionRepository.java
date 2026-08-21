package backend.preparation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

interface SpringDataPatientAccessSessionRepository extends JpaRepository<PatientAccessSessionJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientAccessSessionJpaEntity> findByInvitationId(UUID invitationId);
}
