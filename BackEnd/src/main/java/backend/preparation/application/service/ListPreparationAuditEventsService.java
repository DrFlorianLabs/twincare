package backend.preparation.application.service;

import java.util.Objects;
import java.util.OptionalLong;

import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.ListPreparationAuditEventsCommand;
import backend.preparation.application.port.in.ListPreparationAuditEventsUseCase;
import backend.preparation.application.port.in.PreparationAuditEventPageResult;
import backend.preparation.application.port.in.PreparationAuditEventResult;
import backend.preparation.application.port.out.PreparationAuditEventReader;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;

public final class ListPreparationAuditEventsService implements ListPreparationAuditEventsUseCase {

    private final ProfessionalPreparationAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final PreparationAuditEventReader events;

    public ListPreparationAuditEventsService(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationAuditEventReader events) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public PreparationAuditEventPageResult list(ListPreparationAuditEventsCommand command) {
        Objects.requireNonNull(command, "Audit event query is required");
        authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.VIEW_PREPARATION_AUDIT);
        preparationCases.findById(command.preparationCaseId(), command.organizationId())
                .orElseThrow(PreparationCaseNotFoundException::new);

        var page = events.readPage(
                command.organizationId(),
                command.preparationCaseId(),
                command.beforeSequence(),
                command.limit());
        var items = page.events().stream()
                .map(event -> new PreparationAuditEventResult(
                        event.sequence(),
                        event.type(),
                        event.eventVersion(),
                        event.actorKind(),
                        event.actorUserId(),
                        event.occurredAt(),
                        event.caseRevision(),
                        event.subject()))
                .toList();
        OptionalLong nextCursor = page.hasMore() && !items.isEmpty()
                ? OptionalLong.of(items.get(items.size() - 1).sequence())
                : OptionalLong.empty();
        return new PreparationAuditEventPageResult(
                items, nextCursor, page.completeFromCaseCreation());
    }
}
