package backend.clinicalcontext.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import backend.clinicalcontext.application.ClinicalFactConflictException;
import backend.clinicalcontext.application.port.out.AuthorizedClinicalReviewer;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;
import backend.preparation.domain.PreparationAuditSubjectType;
import backend.clinicalcontext.domain.FactReviewStatus;

import backend.clinicalcontext.application.port.in.ReviewClinicalFactCommand;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ClinicalReviewerReference;
import backend.clinicalcontext.domain.ConsultationReasonFact;
import backend.clinicalcontext.domain.FactReviewDecision;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationCaseStatus;

class ReviewClinicalFactServiceTest {

    @Test
    void normalizesNanosecondClockBeforePersistenceAndResponse() {
        var organization = new OrganizationId("synthetic-review-org");
        var caseId = new PreparationCaseId(UUID.randomUUID());
        var contextId = new ClinicalContextId(caseId.value());
        var owner = new ClinicalContextOwner(organization.value());
        var factId = new ClinicalFactId(UUID.randomUUID());
        var fact = ConsultationReasonFact.declaredByPatient(
                factId, contextId, owner, "Motif fictif",
                Instant.parse("2026-09-04T09:00:00Z"));
        var preparation = mock(PreparationCase.class);
        var cases = mock(PreparationCaseRepository.class);
        var facts = mock(ClinicalFactRepository.class);
        var audit = mock(PreparationAuditEventStore.class);
        when(preparation.status()).thenReturn(PreparationCaseStatus.COLLECTING);
        when(cases.findByIdForUpdate(caseId, organization)).thenReturn(Optional.of(preparation));
        when(facts.findByIdForUpdate(factId, contextId, owner)).thenReturn(Optional.of(fact));
        when(cases.update(preparation)).thenReturn(7L);
        var service = new ReviewClinicalFactService(
                ignored -> new AuthorizedClinicalReviewer(
                        new ClinicalReviewerReference("synthetic-reviewer"), 42),
                cases, facts, audit,
                Clock.fixed(Instant.parse("2026-09-04T09:05:42.342770711Z"), ZoneOffset.UTC));

        var result = service.review(new ReviewClinicalFactCommand(
                organization, caseId, factId, FactReviewDecision.CONFIRM));

        Instant expected = Instant.parse("2026-09-04T09:05:42.342770Z");
        assertThat(result.reviewedAt()).isEqualTo(expected);
        assertThat(fact.review().reviewedAt()).contains(expected);
        verify(facts).update(fact);
        verify(preparation).touchVisibleContent(expected);
        var event = ArgumentCaptor.forClass(PreparationAuditEvent.class);
        verify(audit).append(event.capture());
        assertThat(event.getValue().type()).isEqualTo(PreparationAuditEventType.CLINICAL_FACT_CONFIRMED);
        assertThat(event.getValue().actorUserId()).hasValue(42);
        assertThat(event.getValue().caseRevision()).isEqualTo(7L);
        assertThat(event.getValue().occurredAt()).isEqualTo(expected);
        assertThat(event.getValue().subject().orElseThrow().type())
                .isEqualTo(PreparationAuditSubjectType.CLINICAL_FACT);
        assertThat(event.getValue().subject().orElseThrow().id()).isEqualTo(factId.value());

        var retry = service.review(new ReviewClinicalFactCommand(
                organization, caseId, factId, FactReviewDecision.CONFIRM));
        assertThat(retry).isEqualTo(result);
        verify(facts, times(1)).update(fact);
        verify(cases, times(1)).update(preparation);
        verify(audit, times(1)).append(any());

    }

    @Test
    void readyContextCannotAcquireANewReviewButAnIdenticalReviewCanBeReplayed() {
        var organization = new OrganizationId("synthetic-ready-review-org");
        var caseId = new PreparationCaseId(UUID.randomUUID());
        var contextId = new ClinicalContextId(caseId.value());
        var owner = new ClinicalContextOwner(organization.value());
        var factId = new ClinicalFactId(UUID.randomUUID());
        var recordedAt = Instant.parse("2026-09-04T09:00:00Z");
        var fact = ConsultationReasonFact.declaredByPatient(
                factId, contextId, owner, "Motif fictif prêt", recordedAt);
        var preparation = mock(PreparationCase.class);
        var cases = mock(PreparationCaseRepository.class);
        var facts = mock(ClinicalFactRepository.class);
        var audit = mock(PreparationAuditEventStore.class);
        when(preparation.status()).thenReturn(PreparationCaseStatus.READY_FOR_CONSULTATION);
        when(cases.findByIdForUpdate(caseId, organization)).thenReturn(Optional.of(preparation));
        when(facts.findByIdForUpdate(factId, contextId, owner)).thenReturn(Optional.of(fact));
        var reviewer = new ClinicalReviewerReference("synthetic-ready-reviewer");
        var service = new ReviewClinicalFactService(
                ignored -> new AuthorizedClinicalReviewer(reviewer, 42),
                cases, facts, audit,
                Clock.fixed(recordedAt.plusSeconds(300), ZoneOffset.UTC));
        var command = new ReviewClinicalFactCommand(
                organization, caseId, factId, FactReviewDecision.CONFIRM);

        assertThatThrownBy(() -> service.review(command))
                .isInstanceOf(ClinicalFactConflictException.class);
        assertThat(fact.review().status()).isEqualTo(FactReviewStatus.UNREVIEWED);

        fact.review(FactReviewDecision.CONFIRM, reviewer, recordedAt.plusSeconds(60));
        var replay = service.review(command);
        assertThat(replay.reviewedAt()).isEqualTo(recordedAt.plusSeconds(60));
        assertThatThrownBy(() -> service.review(new ReviewClinicalFactCommand(
                organization, caseId, factId, FactReviewDecision.REJECT)))
                .isInstanceOf(ClinicalFactConflictException.class);
        verify(facts, never()).update(any());
        verify(cases, never()).update(any());
        verify(audit, never()).append(any());
    }

    @Test
    void authorizedReviewerRequiresAPositiveAuditIdentityAndRedactsIt() {
        var reference = new ClinicalReviewerReference("synthetic-reviewer");
        assertThatThrownBy(() -> new AuthorizedClinicalReviewer(reference, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizedClinicalReviewer(reference, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AuthorizedClinicalReviewer(reference, 42).toString())
                .isEqualTo("AuthorizedClinicalReviewer[identity redacted]");
    }
}
