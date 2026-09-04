package backend.clinicalcontext.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

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
        when(preparation.status()).thenReturn(PreparationCaseStatus.COLLECTING);
        when(cases.findByIdForUpdate(caseId, organization)).thenReturn(Optional.of(preparation));
        when(facts.findByIdForUpdate(factId, contextId, owner)).thenReturn(Optional.of(fact));
        var service = new ReviewClinicalFactService(
                ignored -> new ClinicalReviewerReference("synthetic-reviewer"),
                cases, facts,
                Clock.fixed(Instant.parse("2026-09-04T09:05:42.342770711Z"), ZoneOffset.UTC));

        var result = service.review(new ReviewClinicalFactCommand(
                organization, caseId, factId, FactReviewDecision.CONFIRM));

        Instant expected = Instant.parse("2026-09-04T09:05:42.342770Z");
        assertThat(result.reviewedAt()).isEqualTo(expected);
        assertThat(fact.review().reviewedAt()).contains(expected);
        verify(facts).update(fact);
        verify(preparation).touchVisibleContent(expected);
    }
}
