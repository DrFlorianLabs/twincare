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
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.exception.UnauthorizedActionException;
import backend.preparation.application.port.in.ListPreparationAuditEventsCommand;
import backend.preparation.application.port.out.PreparationAuditEventPageSnapshot;
import backend.preparation.application.port.out.PreparationAuditEventReader;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.StoredPreparationAuditEvent;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationAuditActorKind;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;

class ListPreparationAuditEventsServiceTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void authorizesBeforeCaseAndAuditLookupThenReturnsStableCursor() {
        var authorization = mock(ProfessionalPreparationAuthorization.class);
        var cases = mock(PreparationCaseRepository.class);
        var events = mock(PreparationAuditEventReader.class);
        var service = new ListPreparationAuditEventsService(authorization, cases, events);
        when(cases.findById(CASE_ID, ORGANIZATION_ID)).thenReturn(Optional.of(preparationCase()));
        when(events.readPage(ORGANIZATION_ID, CASE_ID, OptionalLong.empty(), 1))
                .thenReturn(new PreparationAuditEventPageSnapshot(
                        List.of(storedEvent(3)), true, true));

        var result = service.list(command(1));

        assertThat(result.items()).hasSize(1);
        assertThat(result.nextBeforeSequence()).hasValue(3);
        assertThat(result.completeFromCaseCreation()).isTrue();
        var order = inOrder(authorization, cases, events);
        order.verify(authorization).requireAccess(
                ORGANIZATION_ID, PreparationCaseCapability.VIEW_PREPARATION_AUDIT);
        order.verify(cases).findById(CASE_ID, ORGANIZATION_ID);
        order.verify(events).readPage(ORGANIZATION_ID, CASE_ID, OptionalLong.empty(), 1);
    }

    @Test
    void deniedAccessStopsBeforeAnyCaseOrEventLookup() {
        var authorization = mock(ProfessionalPreparationAuthorization.class);
        var cases = mock(PreparationCaseRepository.class);
        var events = mock(PreparationAuditEventReader.class);
        var service = new ListPreparationAuditEventsService(authorization, cases, events);
        when(authorization.requireAccess(
                ORGANIZATION_ID, PreparationCaseCapability.VIEW_PREPARATION_AUDIT))
                .thenThrow(new UnauthorizedActionException("forbidden"));

        assertThatThrownBy(() -> service.list(command(25)))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(cases, never()).findById(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(events, never()).readPage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    private ListPreparationAuditEventsCommand command(int limit) {
        return new ListPreparationAuditEventsCommand(
                ORGANIZATION_ID, CASE_ID, OptionalLong.empty(), limit);
    }

    private StoredPreparationAuditEvent storedEvent(long sequence) {
        return new StoredPreparationAuditEvent(
                sequence,
                PreparationAuditEventType.READY_CONFIRMED,
                1,
                PreparationAuditActorKind.STAFF,
                OptionalLong.of(42),
                NOW,
                2,
                Optional.empty());
    }

    private PreparationCase preparationCase() {
        return PreparationCase.create(
                CASE_ID,
                ORGANIZATION_ID,
                new PatientReference("synthetic-patient"),
                Optional.of(new AppointmentReference("synthetic-appointment")),
                Optional.empty(),
                NOW);
    }
}
