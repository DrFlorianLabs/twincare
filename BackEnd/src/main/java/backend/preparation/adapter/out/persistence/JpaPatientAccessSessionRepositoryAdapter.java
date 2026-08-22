package backend.preparation.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientAccessSessionScope;
import backend.preparation.application.port.out.PatientReasonLookup;
import backend.preparation.application.port.out.PatientReasonReader;
import backend.preparation.application.port.out.PatientReasonSnapshot;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientAccessSession;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientInvitationStatus;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.ProvenanceType;
import backend.preparation.domain.TokenDigest;

@Repository
public class JpaPatientAccessSessionRepositoryAdapter implements PatientAccessSessionRepository, PatientReasonReader {

    private static final List<PreparationCaseStatus> PATIENT_CONTRIBUTION_STATUSES = Arrays.stream(
                    PreparationCaseStatus.values())
            .filter(PreparationCaseStatus::allowsPatientContribution)
            .toList();

    private final SpringDataPatientAccessSessionRepository repository;

    public JpaPatientAccessSessionRepositoryAdapter(SpringDataPatientAccessSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PatientAccessSession session) {
        repository.saveAndFlush(new PatientAccessSessionJpaEntity(session));
    }

    @Override
    public Optional<PatientAccessSessionScope> findScopeByTokenDigest(TokenDigest tokenDigest) {
        return repository.findScopeByTokenDigest(tokenDigest.keyId(), tokenDigest.value())
                .map(this::toScope);
    }

    @Override
    public Optional<PatientAccessSessionScope> findScopeByIdentity(
            UUID id,
            PatientInvitationId invitationId,
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId) {
        return repository.findScopeByIdentity(
                        id,
                        invitationId.value(),
                        preparationCaseId.value(),
                        organizationId.value())
                .map(this::toScope);
    }

    @Override
    public Optional<PatientReasonSnapshot> findForActivePatientSession(PatientReasonLookup lookup) {
        return repository.findReasonForActivePatientSession(
                        lookup.sessionId(),
                        lookup.invitationId().value(),
                        lookup.preparationCaseId().value(),
                        lookup.organizationId().value(),
                        lookup.sessionExpiresAt(),
                        lookup.readAt(),
                        PatientAccessSessionScope.CONTRIBUTION_SCOPE,
                        PATIENT_CONTRIBUTION_STATUSES,
                        PatientInvitationStatus.CONSUMED,
                        ProvenanceType.PATIENT_DECLARED)
                .map(this::toPatientReasonSnapshot);
    }

    @Override
    public Optional<PatientAccessSession> findByIdForUpdate(
            UUID id,
            PatientInvitationId invitationId,
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId) {
        return repository.findByIdAndInvitationIdAndPreparationCaseIdAndOrganizationId(
                        id,
                        invitationId.value(),
                        preparationCaseId.value(),
                        organizationId.value())
                .map(PatientAccessSessionJpaEntity::toDomain);
    }

    @Override
    public List<PatientAccessSession> findAllByCaseForUpdate(
            PreparationCaseId preparationCaseId,
            OrganizationId organizationId) {
        return repository.findAllByPreparationCaseIdAndOrganizationIdOrderById(
                        preparationCaseId.value(), organizationId.value())
                .stream()
                .map(PatientAccessSessionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void update(PatientAccessSession session) {
        PatientAccessSessionJpaEntity entity = repository
                .findByIdAndInvitationIdAndPreparationCaseIdAndOrganizationId(
                        session.id(),
                        session.invitationId().value(),
                        session.preparationCaseId().value(),
                        session.organizationId().value())
                .orElseThrow(() -> new IllegalStateException(
                        "Patient access session disappeared during update"));
        entity.updateFrom(session);
        repository.saveAndFlush(entity);
    }

    @Override
    public boolean revokeByInvitationId(PatientInvitationId invitationId, Instant occurredAt) {
        return repository.findByInvitationId(invitationId.value())
                .map(entity -> {
                    if (!entity.revoke(occurredAt)) {
                        return false;
                    }
                    repository.saveAndFlush(entity);
                    return true;
                })
                .orElse(false);
    }

    private PatientAccessSessionScope toScope(
            SpringDataPatientAccessSessionRepository.PatientAccessSessionScopeView view) {
        return new PatientAccessSessionScope(
                view.getSessionId(),
                new PatientInvitationId(view.getInvitationId()),
                new OrganizationId(view.getOrganizationId()),
                new PreparationCaseId(view.getPreparationCaseId()),
                view.getScope(),
                view.getInvitationStatus(),
                view.getPreparationCaseStatus(),
                view.getIssuedAt(),
                view.getExpiresAt(),
                Optional.ofNullable(view.getRevokedAt()));
    }

    private PatientReasonSnapshot toPatientReasonSnapshot(
            SpringDataPatientAccessSessionRepository.PatientReasonView view) {
        if ((view.getPatientReason() == null) != (view.getPatientReasonRecordedAt() == null)) {
            throw new IllegalStateException("Patient reason persistence evidence is inconsistent");
        }
        var reason = Optional.ofNullable(view.getPatientReason())
                .map(value -> PatientStatement.declaredByPatient(value, view.getPatientReasonRecordedAt()));
        return new PatientReasonSnapshot(reason);
    }
}
