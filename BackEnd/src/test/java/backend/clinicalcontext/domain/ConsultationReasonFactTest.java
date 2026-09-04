package backend.clinicalcontext.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConsultationReasonFactTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-08-28T08:00:00Z");

    @Test
    void createsOneAtomicPatientDeclaredFactWithoutInventingClinicalCertainty() {
        ConsultationReasonFact fact = newFact("  Fatigue synthétique  ");

        assertThat(fact.text()).isEqualTo("Fatigue synthétique");
        assertThat(fact.kind()).isEqualTo(ClinicalFactKind.CONSULTATION_REASON);
        assertThat(fact.provenance()).isEqualTo(ProvenanceType.PATIENT_DECLARED);
        assertThat(fact.certainty()).isEqualTo(FactCertainty.NOT_ASSESSED);
        assertThat(fact.temporal().recordedAt()).isEqualTo(RECORDED_AT);
        assertThat(fact.temporal().effectiveFrom()).isEmpty();
        assertThat(fact.temporal().effectiveTo()).isEmpty();
        assertThat(fact.lifecycleStatus()).isEqualTo(FactLifecycleStatus.CURRENT);
        assertThat(fact.review().status()).isEqualTo(FactReviewStatus.UNREVIEWED);
        assertThat(fact.toString()).doesNotContain("Fatigue synthétique");
    }

    @Test
    void supersedesWithoutDestroyingTheOriginalAssertion() {
        ConsultationReasonFact fact = newFact("Premier motif synthétique");
        Instant supersededAt = RECORDED_AT.plusSeconds(60);

        assertThat(fact.supersede(supersededAt)).isTrue();

        assertThat(fact.text()).isEqualTo("Premier motif synthétique");
        assertThat(fact.lifecycleStatus()).isEqualTo(FactLifecycleStatus.SUPERSEDED);
        assertThat(fact.supersededAt()).contains(supersededAt);
        assertThat(fact.supersede(supersededAt.plusSeconds(1))).isFalse();
    }

    @Test
    void reviewIsHumanExplicitAndIdempotentForTheSameDecision() {
        ConsultationReasonFact fact = newFact("Motif synthétique");
        Instant reviewedAt = RECORDED_AT.plusSeconds(30);
        ClinicalReviewerReference reviewer = new ClinicalReviewerReference("synthetic-reviewer");

        assertThat(fact.review(FactReviewDecision.CONFIRM, reviewer, reviewedAt)).isTrue();
        assertThat(fact.review().status()).isEqualTo(FactReviewStatus.CONFIRMED);
        assertThat(fact.review().reviewedAt()).contains(reviewedAt);
        assertThat(fact.review(FactReviewDecision.CONFIRM, reviewer, reviewedAt.plusSeconds(10))).isFalse();
        assertThat(fact.review().reviewedAt()).contains(reviewedAt);
    }

    @Test
    void failsClosedWhenReplacingAFinalReviewOrReviewingSupersededContent() {
        ConsultationReasonFact reviewed = newFact("Motif synthétique confirmé");
        reviewed.review(
                FactReviewDecision.CONFIRM,
                new ClinicalReviewerReference("synthetic-reviewer"),
                RECORDED_AT.plusSeconds(10));

        assertThatThrownBy(() -> reviewed.review(
                        FactReviewDecision.REJECT,
                        new ClinicalReviewerReference("synthetic-reviewer"),
                        RECORDED_AT.plusSeconds(20)))
                .isInstanceOf(InvalidClinicalFactTransitionException.class);

        ConsultationReasonFact superseded = newFact("Ancien motif synthétique");
        superseded.supersede(RECORDED_AT.plusSeconds(10));
        assertThatThrownBy(() -> superseded.review(
                        FactReviewDecision.CONFIRM,
                        new ClinicalReviewerReference("synthetic-reviewer"),
                        RECORDED_AT.plusSeconds(20)))
                .isInstanceOf(InvalidClinicalFactTransitionException.class);
    }

    @Test
    void rejectsInvalidTextAndChronologyWithoutEchoingClinicalContent() {
        String oversized = "x".repeat(ConsultationReasonFact.MAX_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> newFact(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(oversized);
        ConsultationReasonFact fact = newFact("Motif synthétique");
        assertThatThrownBy(() -> fact.supersede(RECORDED_AT.minusSeconds(1)))
                .isInstanceOf(InvalidClinicalFactTransitionException.class);
    }

    private ConsultationReasonFact newFact(String text) {
        return ConsultationReasonFact.declaredByPatient(
                new ClinicalFactId(UUID.fromString("c7390fe9-5fcb-427c-bf58-fb1ac16f9a75")),
                new ClinicalContextId(UUID.fromString("c857395f-45cb-45de-bb54-d7bf5f0e4194")),
                new ClinicalContextOwner("synthetic-clinic"),
                text,
                RECORDED_AT);
    }
}
