package backend.preparation.adapter.in.web;

import java.util.OptionalLong;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import backend.exception.BadRequestException;
import backend.preparation.application.port.in.ListPreparationAuditEventsCommand;
import backend.preparation.application.port.in.ListPreparationAuditEventsUseCase;
import backend.preparation.application.port.in.PreparationAuditEventResult;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases/{caseId}/audit-events")
public final class ProfessionalPreparationAuditController {

    private final ListPreparationAuditEventsUseCase listAuditEvents;

    public ProfessionalPreparationAuditController(ListPreparationAuditEventsUseCase listAuditEvents) {
        this.listAuditEvents = listAuditEvents;
    }

    @GetMapping
    public ResponseEntity<PreparationAuditEventPageResponse> list(
            @PathVariable String organizationId,
            @PathVariable String caseId,
            @RequestParam(required = false) Long beforeSequence,
            @RequestParam(defaultValue = "25") int limit) {
        ListPreparationAuditEventsCommand command;
        try {
            command = new ListPreparationAuditEventsCommand(
                    new OrganizationId(organizationId),
                    new PreparationCaseId(UUID.fromString(caseId)),
                    beforeSequence == null ? OptionalLong.empty() : OptionalLong.of(beforeSequence),
                    limit);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Requête de journal d'audit invalide");
        }
        var result = listAuditEvents.list(command);
        var response = new PreparationAuditEventPageResponse(
                result.items().stream().map(this::toResponse).toList(),
                result.nextBeforeSequence().isPresent()
                        ? result.nextBeforeSequence().getAsLong()
                        : null,
                result.completeFromCaseCreation());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    private PreparationAuditEventResponse toResponse(PreparationAuditEventResult event) {
        var subject = event.subject()
                .map(value -> new PreparationAuditSubjectResponse(value.type().name(), value.id()))
                .orElse(null);
        return new PreparationAuditEventResponse(
                event.sequence(),
                event.type().name(),
                event.eventVersion(),
                event.actorKind().name(),
                event.actorUserId().isPresent() ? event.actorUserId().getAsLong() : null,
                event.occurredAt(),
                event.caseRevision(),
                subject);
    }
}
