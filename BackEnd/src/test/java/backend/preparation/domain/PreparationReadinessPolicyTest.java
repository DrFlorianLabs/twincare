package backend.preparation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class PreparationReadinessPolicyTest {

    private final PreparationReadinessPolicy policy = new PreparationReadinessPolicy();

    @Test
    void missingReasonIsIncomplete() {
        var assessment = policy.assessCurrent(
                PreparationCaseStatus.PATIENT_STARTED,
                false,
                0,
                false);

        assertThat(assessment.state()).isEqualTo(PreparationReadinessState.INCOMPLETE);
        assertThat(assessment.blockers())
                .containsExactly(PreparationReadinessBlocker.PATIENT_REASON_MISSING);
        assertThat(assessment.ruleSetId()).isEqualTo("CORE_PREPARATION");
        assertThat(assessment.ruleSetVersion()).isEqualTo(1);
        assertThat(assessment.provenance())
                .isEqualTo(PreparationReadinessProvenance.DERIVED_DETERMINISTIC);
    }

    @Test
    void openHumanTaskRequiresAction() {
        var assessment = policy.assessCurrent(
                PreparationCaseStatus.NEEDS_ACTION,
                true,
                1,
                false);

        assertThat(assessment.state()).isEqualTo(PreparationReadinessState.ACTION_REQUIRED);
        assertThat(assessment.blockers())
                .containsExactly(PreparationReadinessBlocker.OPEN_PREPARATION_TASKS);
    }

    @Test
    void patientReasonWithoutOpenTaskIsReviewableRegardlessOfOptionalAttachment() {
        var assessment = policy.assessCurrent(
                PreparationCaseStatus.COLLECTING,
                true,
                0,
                false);

        assertThat(assessment.state()).isEqualTo(PreparationReadinessState.REVIEWABLE);
        assertThat(assessment.blockers()).isEmpty();
    }

    @Test
    void consistentProfessionalConfirmationIsReady() {
        var assessment = policy.assessCurrent(
                PreparationCaseStatus.READY_FOR_CONSULTATION,
                true,
                0,
                true);

        assertThat(assessment.state()).isEqualTo(PreparationReadinessState.READY);
        assertThat(assessment.blockers()).isEmpty();
    }

    @Test
    void everyTerminalStateIsNotApplicable() {
        EnumSet.of(
                        PreparationCaseStatus.CLOSED,
                        PreparationCaseStatus.EXPIRED,
                        PreparationCaseStatus.CANCELLED)
                .forEach(status -> assertThat(policy.assessCurrent(status, false, 3, false).state())
                        .isEqualTo(PreparationReadinessState.NOT_APPLICABLE));
    }

    @Test
    void unknownRulesAndInconsistentReadyEvidenceFailClosed() {
        assertThatThrownBy(() -> policy.assess(
                "UNKNOWN",
                1,
                PreparationCaseStatus.COLLECTING,
                true,
                0,
                false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.assessCurrent(
                PreparationCaseStatus.READY_FOR_CONSULTATION,
                true,
                1,
                true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.assessCurrent(
                PreparationCaseStatus.COLLECTING,
                true,
                0,
                true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.assessCurrent(
                PreparationCaseStatus.NEEDS_ACTION,
                true,
                0,
                false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.assessCurrent(
                PreparationCaseStatus.COLLECTING,
                true,
                1,
                false))
                .isInstanceOf(IllegalStateException.class);
    }
}
