package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.in.CreatePreparationCaseResult;
import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparationCaseIdGenerator;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.InvalidPreparationCaseCommandException;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;

import java.util.Optional;

public final class CreatePreparationCaseService implements CreatePreparationCaseUseCase {

    private final PreparationCaseAuthorization authorization;
    private final PreparationCaseRepository repository;
    private final PreparationCaseIdGenerator idGenerator;
    private final PreparationAuditEventStore auditEvents;
    private final Clock clock;

    public CreatePreparationCaseService(
            PreparationCaseAuthorization authorization,
            PreparationCaseRepository repository,
            PreparationCaseIdGenerator idGenerator,
            PreparationAuditEventStore auditEvents,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.repository = Objects.requireNonNull(repository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.auditEvents = Objects.requireNonNull(auditEvents);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public CreatePreparationCaseResult create(CreatePreparationCaseCommand command) {
        Objects.requireNonNull(command, "Creation command is required");
        long actorUserId = authorization.requireStaffAccess(command.organizationId());

        Instant now = clock.instant();
        PreparationCaseId id = idGenerator.nextId();
        PreparationCase preparationCase;
        try {
            preparationCase = PreparationCase.create(
                    id,
                    command.organizationId(),
                    command.patientReference(),
                    command.appointmentReference(),
                    command.scheduledAt(),
                    now);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPreparationCaseCommandException();
        }
        repository.add(preparationCase);
        auditEvents.append(PreparationAuditEvent.staff(
                preparationCase.organizationId(),
                preparationCase.id(),
                PreparationAuditEventType.PREPARATION_CREATED,
                actorUserId,
                now,
                preparationCase.revision(),
                Optional.empty()));

        return new CreatePreparationCaseResult(
                preparationCase.id(),
                preparationCase.organizationId(),
                preparationCase.status(),
                preparationCase.createdAt());
    }
}
