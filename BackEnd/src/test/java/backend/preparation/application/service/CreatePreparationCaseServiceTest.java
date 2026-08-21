package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.exception.UnauthorizedActionException;
import backend.preparation.application.InvalidPreparationCaseCommandException;
import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationCaseRepository;
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

    @Test
    void authorizesThenCreatesAndPersistsTheCase() {
        RecordingAuthorization authorization = new RecordingAuthorization();
        RecordingRepository repository = new RecordingRepository();
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                authorization,
                repository,
                () -> CASE_ID,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.create(command());

        assertThat(authorization.authorizedOrganization).isEqualTo(ORGANIZATION_ID);
        assertThat(repository.added).isNotNull();
        assertThat(repository.added.id()).isEqualTo(CASE_ID);
        assertThat(repository.added.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(repository.added.status()).isEqualTo(PreparationCaseStatus.CREATED);
        assertThat(result.id()).isEqualTo(CASE_ID);
        assertThat(result.createdAt()).isEqualTo(NOW);
    }

    @Test
    void stopsBeforeGeneratingOrPersistingWhenAuthorizationFails() {
        RecordingRepository repository = new RecordingRepository();
        boolean[] idGenerated = { false };
        PreparationCaseAuthorization denied = organizationId -> {
            throw new UnauthorizedActionException("Accès interdit à cette organisation");
        };
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                denied,
                repository,
                () -> {
                    idGenerated[0] = true;
                    return CASE_ID;
                },
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
                organizationId -> 17L,
                repository,
                () -> CASE_ID,
                Clock.fixed(NOW, ZoneOffset.UTC));
        CreatePreparationCaseCommand pastDeadline = new CreatePreparationCaseCommand(
                ORGANIZATION_ID,
                new PatientReference("patient-42"),
                Optional.empty(),
                Optional.of(NOW.minusSeconds(1)));

        assertThatThrownBy(() -> service.create(pastDeadline))
                .isInstanceOf(InvalidPreparationCaseCommandException.class);
        assertThat(repository.added).isNull();
    }

    private CreatePreparationCaseCommand command() {
        return new CreatePreparationCaseCommand(
                ORGANIZATION_ID,
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.of(NOW.plusSeconds(3600)));
    }

    private static final class RecordingAuthorization implements PreparationCaseAuthorization {
        private OrganizationId authorizedOrganization;

        @Override
        public long requireStaffAccess(OrganizationId organizationId) {
            authorizedOrganization = organizationId;
            return 17L;
        }
    }

    private static final class RecordingRepository implements PreparationCaseRepository {
        private PreparationCase added;

        @Override
        public void add(PreparationCase preparationCase) {
            added = preparationCase;
        }

        @Override
        public void update(PreparationCase preparationCase) {
            added = preparationCase;
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
    }
}
