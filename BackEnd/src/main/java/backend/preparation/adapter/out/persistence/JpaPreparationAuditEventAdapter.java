package backend.preparation.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import backend.preparation.application.port.out.PreparationAuditEventPageSnapshot;
import backend.preparation.application.port.out.PreparationAuditEventReader;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.StoredPreparationAuditEvent;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubject;
import backend.preparation.domain.PreparationCaseId;

@Repository
public class JpaPreparationAuditEventAdapter
        implements PreparationAuditEventStore, PreparationAuditEventReader {

    private final SpringDataPreparationAuditEventRepository repository;

    public JpaPreparationAuditEventAdapter(SpringDataPreparationAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(PreparationAuditEvent event) {
        long nextSequence = repository.findMaxSequence(
                event.organizationId().value(), event.preparationCaseId().value()) + 1;
        var subject = event.subject().orElse(null);
        repository.saveAndFlush(new PreparationAuditEventJpaEntity(
                event.id(),
                event.organizationId().value(),
                event.preparationCaseId().value(),
                nextSequence,
                event.type(),
                event.eventVersion(),
                event.actorKind(),
                event.actorUserId().isPresent() ? event.actorUserId().getAsLong() : null,
                event.occurredAt(),
                event.caseRevision(),
                subject == null ? null : subject.type(),
                subject == null ? null : subject.id()));
    }

    @Override
    public PreparationAuditEventPageSnapshot readPage(
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            OptionalLong beforeSequence,
            int limit) {
        var page = PageRequest.of(0, limit + 1);
        List<PreparationAuditEventJpaEntity> loaded = beforeSequence.isPresent()
                ? repository.findBefore(
                        organizationId.value(), preparationCaseId.value(), beforeSequence.getAsLong(), page)
                : repository.findLatest(organizationId.value(), preparationCaseId.value(), page);
        boolean hasMore = loaded.size() > limit;
        var events = loaded.stream().limit(limit).map(this::toSnapshot).toList();
        boolean complete = repository.existsByOrganizationIdAndPreparationCaseIdAndEventTypeAndSequence(
                organizationId.value(),
                preparationCaseId.value(),
                PreparationAuditEventType.PREPARATION_CREATED,
                1);
        return new PreparationAuditEventPageSnapshot(events, hasMore, complete);
    }

    private StoredPreparationAuditEvent toSnapshot(PreparationAuditEventJpaEntity entity) {
        return new StoredPreparationAuditEvent(
                entity.sequence(),
                entity.eventType(),
                entity.eventVersion(),
                entity.actorKind(),
                entity.actorUserId() == null
                        ? OptionalLong.empty()
                        : OptionalLong.of(entity.actorUserId()),
                entity.occurredAt(),
                entity.caseRevision(),
                entity.subjectType() == null
                        ? Optional.empty()
                        : Optional.of(new PreparationAuditSubject(entity.subjectType(), entity.subjectId())));
    }
}
