package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.exception.UnauthorizedActionException;
import backend.preparation.application.InvalidPreparationCaseCommandException;
import backend.preparation.application.PreparationCaseConflictException;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseCreationFingerprint;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.StoredPreparationCaseCreation;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;

class CreatePreparationCaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("0ef5bc41-e339-4a74-9247-485052421b9a"));
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("cabinet-a");
    private static final UUID REQUEST_ID = UUID.fromString("fb900ab6-f211-4c8a-aa54-782255e26782");

    @Test
    void authorizesThenCreatesAndPersistsTheCase() {
        RecordingAuthorization authorization = new RecordingAuthorization();
        RecordingRepository repository = new RecordingRepository();
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                authorization,
                repository,
                () -> CASE_ID,
                event -> { },
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.create(command());

        assertThat(authorization.authorizedOrganization).isEqualTo(ORGANIZATION_ID);
        assertThat(authorization.capability).isEqualTo(PreparationCaseCapability.CREATE_PREPARATION_CASE);
        assertThat(repository.added).isNotNull();
        assertThat(repository.added.id()).isEqualTo(CASE_ID);
        assertThat(repository.added.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(repository.added.status()).isEqualTo(PreparationCaseStatus.CREATED);
        assertThat(result.id()).isEqualTo(CASE_ID);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.created()).isTrue();
    }

    @Test
    void stopsBeforeGeneratingOrPersistingWhenAuthorizationFails() {
        RecordingRepository repository = new RecordingRepository();
        boolean[] idGenerated = { false };
        ProfessionalPreparationAuthorization denied = new DeniedAuthorization();
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                denied,
                repository,
                () -> {
                    idGenerated[0] = true;
                    return CASE_ID;
                },
                event -> { },
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(UnauthorizedActionException.class);
        assertThat(idGenerated[0]).isFalse();
        assertThat(repository.added).isNull();
    }

    @Test
    void mapsExpectedDomainValidationWithoutPersisting() {
        RecordingRepository repository = new RecordingRepository();
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                new RecordingAuthorization(),
                repository,
                () -> CASE_ID,
                event -> { },
                Clock.fixed(NOW, ZoneOffset.UTC));
        CreatePreparationCaseCommand pastDeadline = new CreatePreparationCaseCommand(
                REQUEST_ID,
                ORGANIZATION_ID,
                new PatientReference("patient-42"),
                Optional.empty(),
                Optional.of(NOW.minusSeconds(1)));

        assertThatThrownBy(() -> service.create(pastDeadline))
                .isInstanceOf(InvalidPreparationCaseCommandException.class);
        assertThat(repository.added).isNull();
    }

    @Test
    void sameRequestAndNormalizedPayloadReplaysWithoutGeneratingAnIdOrSecondAudit() {
        RecordingRepository repository = new RecordingRepository();
        var audits = new ArrayList<PreparationAuditEvent>();
        int[] generatedIds = { 0 };
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                new RecordingAuthorization(),
                repository,
                () -> {
                    generatedIds[0]++;
                    return CASE_ID;
                },
                audits::add,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var created = service.create(command());
        var replayed = service.create(new CreatePreparationCaseCommand(
                REQUEST_ID,
                ORGANIZATION_ID,
                new PatientReference(" patient-42 "),
                Optional.of(new AppointmentReference(" appointment-7 ")),
                Optional.of(NOW.plusSeconds(3600))));

        assertThat(created.created()).isTrue();
        assertThat(replayed.created()).isFalse();
        assertThat(replayed.id()).isEqualTo(created.id());
        assertThat(generatedIds[0]).isOne();
        assertThat(audits).hasSize(1);
    }

    @Test
    void sameRequestWithDifferentPayloadFailsClosedWithoutMutation() {
        RecordingRepository repository = new RecordingRepository();
        var audits = new ArrayList<PreparationAuditEvent>();
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                new RecordingAuthorization(),
                repository,
                () -> CASE_ID,
                audits::add,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service.create(command());

        var changed = new CreatePreparationCaseCommand(
                REQUEST_ID,
                ORGANIZATION_ID,
                new PatientReference("patient-different"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.of(NOW.plusSeconds(3600)));

        assertThatThrownBy(() -> service.create(changed))
                .isInstanceOf(PreparationCaseConflictException.class);
        assertThat(repository.added.patientReference()).isEqualTo(new PatientReference("patient-42"));
        assertThat(audits).hasSize(1);
    }

    private CreatePreparationCaseCommand command() {
        return new CreatePreparationCaseCommand(
                REQUEST_ID,
                ORGANIZATION_ID,
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.of(NOW.plusSeconds(3600)));
    }

    private static final class RecordingAuthorization implements ProfessionalPreparationAuthorization {
        private OrganizationId authorizedOrganization;
        private PreparationCaseCapability capability;

        @Override
        public long requireAccess(OrganizationId organizationId, PreparationCaseCapability capability) {
            authorizedOrganization = organizationId;
            this.capability = capability;
            return 17L;
        }

        @Override
        public void requireAssignableStaff(OrganizationId organizationId, long userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class DeniedAuthorization implements ProfessionalPreparationAuthorization {
        @Override
        public long requireAccess(OrganizationId organizationId, PreparationCaseCapability capability) {
            throw new UnauthorizedActionException("Accès interdit à cette organisation");
        }

        @Override
        public void requireAssignableStaff(OrganizationId organizationId, long userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingRepository implements PreparationCaseRepository {
        private PreparationCase added;
        private UUID requestId;
        private PreparationCaseCreationFingerprint fingerprint;

        @Override
        public void add(PreparationCase preparationCase) {
            added = preparationCase;
        }

        @Override
        public void add(
                PreparationCase preparationCase,
                UUID requestId,
                PreparationCaseCreationFingerprint fingerprint) {
            added = preparationCase;
            this.requestId = requestId;
            this.fingerprint = fingerprint;
        }

        @Override
        public long update(PreparationCase preparationCase) {
            added = preparationCase;
            return preparationCase.revision() + 1;
        }

        @Override
        public Optional<PreparationCase> findById(PreparationCaseId id, OrganizationId organizationId) {
            return Optional.ofNullable(added)
                    .filter(preparationCase -> preparationCase.id().equals(id))
                    .filter(preparationCase -> preparationCase.organizationId().equals(organizationId));
        }

        @Override
        public Optional<PreparationCase> findByIdForUpdate(PreparationCaseId id, OrganizationId organizationId) {
            return findById(id, organizationId);
        }

        @Override
        public Optional<StoredPreparationCaseCreation> findByCreationRequestId(
                OrganizationId organizationId,
                UUID requestId) {
            return Optional.ofNullable(added)
                    .filter(preparationCase -> preparationCase.organizationId().equals(organizationId))
                    .filter(preparationCase -> requestId.equals(this.requestId))
                    .map(preparationCase -> new StoredPreparationCaseCreation(preparationCase, fingerprint));
        }
    }
}
