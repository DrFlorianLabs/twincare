package backend.preparation.adapter.out.persistence;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitationId;

@Repository
public class JpaPatientAccessSessionRepositoryAdapter implements PatientAccessSessionRepository {

    private final SpringDataPatientAccessSessionRepository repository;

    public JpaPatientAccessSessionRepositoryAdapter(SpringDataPatientAccessSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PatientAccessSession session) {
        repository.saveAndFlush(new PatientAccessSessionJpaEntity(session));
    }

    @Override
    public void revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt) {
        repository.findByInvitationId(invitationId.value()).ifPresent(entity -> {
            entity.revoke(occurredAt);
            repository.saveAndFlush(entity);
        });
    }
}
