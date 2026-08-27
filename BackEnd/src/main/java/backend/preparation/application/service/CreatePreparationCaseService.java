package backend.preparation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.application.InvalidPreparationCaseCommandException;
import backend.preparation.application.PreparationCaseConflictException;
import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.in.CreatePreparationCaseResult;
import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseCreationFingerprint;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparationCaseIdGenerator;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;

public final class CreatePreparationCaseService implements CreatePreparationCaseUseCase {

    private final ProfessionalPreparationAuthorization authorization;
    private final PreparationCaseRepository repository;
    private final PreparationCaseIdGenerator idGenerator;
    private final PreparationAuditEventStore auditEvents;
    private final Clock clock;

    public CreatePreparationCaseService(
            ProfessionalPreparationAuthorization authorization,
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
        long actorUserId = authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.CREATE_PREPARATION_CASE);
        PreparationCaseCreationFingerprint fingerprint = PreparationCaseCreationFingerprintFactory.from(command);
        var replay = replay(command, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

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
        repository.add(preparationCase, command.requestId(), fingerprint);
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
                preparationCase.createdAt(),
                true);
    }

    public CreatePreparationCaseResult replayAfterConflict(CreatePreparationCaseCommand command) {
        Objects.requireNonNull(command, "Creation command is required");
        authorization.requireAccess(
                command.organizationId(), PreparationCaseCapability.CREATE_PREPARATION_CASE);
        return replay(command, PreparationCaseCreationFingerprintFactory.from(command))
                .orElseThrow(PreparationCaseConflictException::new);
    }

    private Optional<CreatePreparationCaseResult> replay(
            CreatePreparationCaseCommand command,
            PreparationCaseCreationFingerprint fingerprint) {
        return repository.findByCreationRequestId(command.organizationId(), command.requestId())
                .map(stored -> {
                    if (!stored.payloadFingerprint().equals(fingerprint)) {
                        throw new PreparationCaseConflictException();
                    }
                    PreparationCase preparationCase = stored.preparationCase();
                    return new CreatePreparationCaseResult(
                            preparationCase.id(),
                            preparationCase.organizationId(),
                            preparationCase.status(),
                            preparationCase.createdAt(),
                            false);
                });
    }
}
