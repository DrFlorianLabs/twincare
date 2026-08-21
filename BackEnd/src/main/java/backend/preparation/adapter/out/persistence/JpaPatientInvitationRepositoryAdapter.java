package backend.preparation.adapter.out.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import backend.preparation.application.PatientInvitationConflictException;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PatientInvitationScope;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitation;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.TokenDigest;

@Repository
public class JpaPatientInvitationRepositoryAdapter implements PatientInvitationRepository {

    private static final String ACTIVE_SLOT = "ACTIVE";

    private final SpringDataPatientInvitationRepository repository;

    public JpaPatientInvitationRepositoryAdapter(SpringDataPatientInvitationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PatientInvitation> findActiveForUpdate(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId) {
        return repository.findByPreparationCaseIdAndOrganizationIdAndActiveSlot(
                        preparationCaseId.value(), organizationId.value(), ACTIVE_SLOT)
                .map(this::toDomain);
    }

    @Override
    public Optional<PatientInvitation> findByIdForUpdate(
            PatientInvitationId invitationId,
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId) {
        return repository.findByIdAndPreparationCaseIdAndOrganizationId(
                        invitationId.value(), preparationCaseId.value(), organizationId.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<PatientInvitation> findByTokenDigestForUpdate(TokenDigest tokenDigest) {
        return repository.findByTokenKeyIdAndTokenDigest(tokenDigest.keyId(), tokenDigest.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<PatientInvitationScope> findScopeByTokenDigest(TokenDigest tokenDigest) {
        return repository.findScopeByTokenDigest(tokenDigest.keyId(), tokenDigest.value())
                .map(view -> new PatientInvitationScope(
                        new PatientInvitationId(view.getId()),
                        new OrganizationId(view.getOrganizationId()),
                        new PreparationCaseId(view.getPreparationCaseId())));
    }

    @Override
    public void add(PatientInvitation invitation) {
        try {
            repository.saveAndFlush(new PatientInvitationJpaEntity(invitation));
        } catch (DataIntegrityViolationException exception) {
            throw new PatientInvitationConflictException();
        }
    }

    @Override
    public void update(PatientInvitation invitation) {
        PatientInvitationJpaEntity managed = repository.findById(invitation.id().value())
                .orElseThrow(() -> new IllegalStateException("Patient invitation disappeared during update"));
        managed.updateFrom(invitation);
        repository.saveAndFlush(managed);
    }

    private PatientInvitation toDomain(PatientInvitationJpaEntity entity) {
        return PatientInvitation.restore(
                new PatientInvitationId(entity.id()),
                new OrganizationId(entity.organizationId()),
                new PreparationCaseId(entity.preparationCaseId()),
                entity.issuedByUserId(),
                new TokenDigest(entity.tokenKeyId(), entity.tokenDigest()),
                entity.status(),
                entity.issuedAt(),
                entity.expiresAt(),
                Optional.ofNullable(entity.consumedAt()),
                Optional.ofNullable(entity.revokedAt()),
                entity.updatedAt());
    }
}
