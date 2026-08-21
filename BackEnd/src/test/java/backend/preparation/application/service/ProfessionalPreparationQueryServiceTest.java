package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import backend.exception.UnauthorizedActionException;
import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.port.in.GetProfessionalPreparationViewCommand;
import backend.preparation.application.port.in.ListProfessionalPreparationsCommand;
import backend.preparation.application.port.in.ProfessionalAttachmentResult;
import backend.preparation.application.port.out.ProfessionalAttachmentSnapshot;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.ProfessionalPreparationListPageSnapshot;
import backend.preparation.application.port.out.ProfessionalPreparationListSnapshot;
import backend.preparation.application.port.out.ProfessionalPreparationQueryRepository;
import backend.preparation.application.port.out.ProfessionalPreparationViewSnapshot;
import backend.preparation.application.port.out.ProfessionalReadyConfirmationSnapshot;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;
import backend.preparation.domain.PreparationReadinessPolicy;
import backend.preparation.domain.PreparationReadinessState;

class ProfessionalPreparationQueryServiceTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private ProfessionalPreparationAuthorization authorization;
    private ProfessionalPreparationQueryRepository repository;
    private ProfessionalPreparationQueryService service;

    @BeforeEach
    void setUp() {
        authorization = mock(ProfessionalPreparationAuthorization.class);
        repository = mock(ProfessionalPreparationQueryRepository.class);
        service = new ProfessionalPreparationQueryService(
                authorization,
                repository,
                new PreparationReadinessPolicy());
    }

    @Test
    void listAuthorizesBeforeTheTenantScopedQueryAndMapsReadiness() {
        var snapshot = new ProfessionalPreparationListSnapshot(
                CASE_ID,
                new PatientReference("synthetic-patient"),
                Optional.of(new AppointmentReference("synthetic-appointment")),
                Optional.of(NOW.plusSeconds(3600)),
                PreparationCaseStatus.COLLECTING,
                true,
                Optional.of(NOW),
                0,
                Optional.empty(),
                false,
                0,
                NOW);
        when(repository.findPage(ORGANIZATION_ID, 0, 20))
                .thenReturn(new ProfessionalPreparationListPageSnapshot(List.of(snapshot), 0, 20, 1, 1));

        var result = service.list(new ListProfessionalPreparationsCommand(ORGANIZATION_ID, 0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().readiness().state())
                .isEqualTo(PreparationReadinessState.REVIEWABLE);
        assertThat(result.content().getFirst().attachmentPresent()).isFalse();
        var ordered = inOrder(authorization, repository);
        ordered.verify(authorization).requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.VIEW_PREPARATION);
        ordered.verify(repository).findPage(ORGANIZATION_ID, 0, 20);
    }

    @Test
    void detailedViewReturnsExactHumanDataWithoutCreatingANarrative() {
        var reason = PatientStatement.declaredByPatient("Synthetic exact reason", NOW);
        var snapshot = new ProfessionalPreparationViewSnapshot(
                CASE_ID,
                new PatientReference("synthetic-patient"),
                Optional.empty(),
                Optional.of(NOW.plusSeconds(3600)),
                PreparationCaseStatus.COLLECTING,
                Optional.of(reason),
                Optional.empty(),
                List.of(),
                0,
                Optional.empty(),
                0,
                NOW.minusSeconds(60),
                NOW);
        when(repository.findView(ORGANIZATION_ID, CASE_ID)).thenReturn(Optional.of(snapshot));

        var result = service.get(new GetProfessionalPreparationViewCommand(ORGANIZATION_ID, CASE_ID));

        assertThat(result.patientReason()).contains(reason);
        assertThat(result.attachment()).isEmpty();
        assertThat(result.readiness().state()).isEqualTo(PreparationReadinessState.REVIEWABLE);
    }

    @Test
    void missingCaseInTheAuthorizedTenantIsNotFound() {
        when(repository.findView(ORGANIZATION_ID, CASE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(
                new GetProfessionalPreparationViewCommand(ORGANIZATION_ID, CASE_ID)))
                .isInstanceOf(PreparationCaseNotFoundException.class);
    }

    @Test
    void readyStatusWithoutPersistedProfessionalConfirmationFailsClosed() {
        var snapshot = new ProfessionalPreparationViewSnapshot(
                CASE_ID,
                new PatientReference("synthetic-patient"),
                Optional.empty(),
                Optional.of(NOW.plusSeconds(3600)),
                PreparationCaseStatus.READY_FOR_CONSULTATION,
                Optional.of(PatientStatement.declaredByPatient("Synthetic exact reason", NOW)),
                Optional.empty(),
                List.of(),
                0,
                Optional.empty(),
                0,
                NOW.minusSeconds(60),
                NOW);
        when(repository.findView(ORGANIZATION_ID, CASE_ID)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.get(
                new GetProfessionalPreparationViewCommand(ORGANIZATION_ID, CASE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void needsActionStatusWithoutPersistedOpenTaskFailsClosed() {
        var snapshot = new ProfessionalPreparationViewSnapshot(
                CASE_ID,
                new PatientReference("synthetic-patient"),
                Optional.empty(),
                Optional.of(NOW.plusSeconds(3600)),
                PreparationCaseStatus.NEEDS_ACTION,
                Optional.of(PatientStatement.declaredByPatient("Synthetic exact reason", NOW)),
                Optional.empty(),
                List.of(),
                0,
                Optional.empty(),
                0,
                NOW.minusSeconds(60),
                NOW);
        when(repository.findView(ORGANIZATION_ID, CASE_ID)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.get(
                new GetProfessionalPreparationViewCommand(ORGANIZATION_ID, CASE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void staleReadyConfirmationRevisionFailsClosed() {
        var reason = PatientStatement.declaredByPatient("Synthetic exact reason", NOW);
        var staleConfirmation = new ProfessionalReadyConfirmationSnapshot(
                42,
                NOW.plusSeconds(1),
                PreparationReadinessPolicy.CURRENT_RULE_SET_ID,
                PreparationReadinessPolicy.CURRENT_RULE_SET_VERSION,
                0,
                NOW,
                0);
        var snapshot = new ProfessionalPreparationViewSnapshot(
                CASE_ID,
                new PatientReference("synthetic-patient"),
                Optional.empty(),
                Optional.of(NOW.plusSeconds(3600)),
                PreparationCaseStatus.READY_FOR_CONSULTATION,
                Optional.of(reason),
                Optional.empty(),
                List.of(),
                0,
                Optional.of(staleConfirmation),
                2,
                NOW.minusSeconds(60),
                NOW.plusSeconds(1));
        when(repository.findView(ORGANIZATION_ID, CASE_ID)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.get(
                new GetProfessionalPreparationViewCommand(ORGANIZATION_ID, CASE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void confirmationForAnotherReasonVersionFailsClosedInTheWorklist() {
        var mismatchedConfirmation = new ProfessionalReadyConfirmationSnapshot(
                42,
                NOW.plusSeconds(1),
                PreparationReadinessPolicy.CURRENT_RULE_SET_ID,
                PreparationReadinessPolicy.CURRENT_RULE_SET_VERSION,
                0,
                NOW.minusSeconds(1),
                0);
        var snapshot = new ProfessionalPreparationListSnapshot(
                CASE_ID,
                new PatientReference("synthetic-patient"),
                Optional.empty(),
                Optional.of(NOW.plusSeconds(3600)),
                PreparationCaseStatus.READY_FOR_CONSULTATION,
                true,
                Optional.of(NOW),
                0,
                Optional.of(mismatchedConfirmation),
                false,
                1,
                NOW.plusSeconds(1));
        when(repository.findPage(ORGANIZATION_ID, 0, 20))
                .thenReturn(new ProfessionalPreparationListPageSnapshot(List.of(snapshot), 0, 20, 1, 1));

        assertThatThrownBy(() -> service.list(
                new ListProfessionalPreparationsCommand(ORGANIZATION_ID, 0, 20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void deniedClinicalAccessStopsBeforeAnyClinicalQuery() {
        when(authorization.requireAccess(
                ORGANIZATION_ID,
                PreparationCaseCapability.VIEW_PREPARATION))
                .thenThrow(new UnauthorizedActionException("forbidden"));

        assertThatThrownBy(() -> service.list(
                new ListProfessionalPreparationsCommand(ORGANIZATION_ID, 0, 20)))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(repository, never()).findPage(ORGANIZATION_ID, 0, 20);
        verify(repository, never()).findView(ORGANIZATION_ID, CASE_ID);
    }

    @Test
    void internalAttachmentRepresentationsRedactTheDisplayFilename() {
        String marker = "synthetic-sensitive-filename.pdf";
        var snapshot = new ProfessionalAttachmentSnapshot(
                marker,
                AttachmentMediaType.PDF,
                42,
                NOW,
                NOW);
        var result = new ProfessionalAttachmentResult(
                marker,
                AttachmentMediaType.PDF,
                42,
                NOW,
                NOW);

        assertThat(snapshot.toString()).doesNotContain(marker);
        assertThat(result.toString()).doesNotContain(marker);
    }
}
