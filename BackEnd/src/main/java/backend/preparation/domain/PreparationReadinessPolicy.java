package backend.preparation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PreparationReadinessPolicy {

    public static final String CURRENT_RULE_SET_ID = "CORE_PREPARATION";
    public static final int CURRENT_RULE_SET_VERSION = 1;

    public PreparationReadinessAssessment assessCurrent(
            PreparationCaseStatus caseStatus,
            boolean patientReasonPresent,
            int openPreparationTaskCount,
            boolean readyConfirmed) {
        return assess(
                CURRENT_RULE_SET_ID,
                CURRENT_RULE_SET_VERSION,
                caseStatus,
                patientReasonPresent,
                openPreparationTaskCount,
                readyConfirmed);
    }

    public PreparationReadinessAssessment assess(
            String ruleSetId,
            int ruleSetVersion,
            PreparationCaseStatus caseStatus,
            boolean patientReasonPresent,
            int openPreparationTaskCount,
            boolean readyConfirmed) {
        requireSupportedRules(ruleSetId, ruleSetVersion);
        Objects.requireNonNull(caseStatus, "Preparation case status is required");
        if (openPreparationTaskCount < 0) {
            throw new IllegalArgumentException("Open preparation task count cannot be negative");
        }

        if (caseStatus.isTerminal()) {
            return assessment(PreparationReadinessState.NOT_APPLICABLE, List.of());
        }
        if ((caseStatus == PreparationCaseStatus.NEEDS_ACTION) != (openPreparationTaskCount > 0)) {
            throw new IllegalStateException("Needs-action status is inconsistent with open preparation tasks");
        }
        if (caseStatus == PreparationCaseStatus.READY_FOR_CONSULTATION
                && (!patientReasonPresent || openPreparationTaskCount > 0 || !readyConfirmed)) {
            throw new IllegalStateException("Ready case is inconsistent with readiness evidence");
        }
        if (readyConfirmed && caseStatus != PreparationCaseStatus.READY_FOR_CONSULTATION) {
            throw new IllegalStateException("Readiness confirmation is inconsistent with case status");
        }

        var blockers = new ArrayList<PreparationReadinessBlocker>();
        if (!patientReasonPresent) {
            blockers.add(PreparationReadinessBlocker.PATIENT_REASON_MISSING);
        }
        if (openPreparationTaskCount > 0) {
            blockers.add(PreparationReadinessBlocker.OPEN_PREPARATION_TASKS);
        }
        if (!patientReasonPresent) {
            return assessment(PreparationReadinessState.INCOMPLETE, blockers);
        }
        if (openPreparationTaskCount > 0) {
            return assessment(PreparationReadinessState.ACTION_REQUIRED, blockers);
        }
        if (readyConfirmed) {
            return assessment(PreparationReadinessState.READY, blockers);
        }
        return assessment(PreparationReadinessState.REVIEWABLE, blockers);
    }

    private void requireSupportedRules(String ruleSetId, int ruleSetVersion) {
        if (!CURRENT_RULE_SET_ID.equals(ruleSetId) || CURRENT_RULE_SET_VERSION != ruleSetVersion) {
            throw new IllegalArgumentException("Unsupported preparation readiness rules");
        }
    }

    private PreparationReadinessAssessment assessment(
            PreparationReadinessState state,
            List<PreparationReadinessBlocker> blockers) {
        return new PreparationReadinessAssessment(
                CURRENT_RULE_SET_ID,
                CURRENT_RULE_SET_VERSION,
                state,
                blockers,
                PreparationReadinessProvenance.DERIVED_DETERMINISTIC);
    }
}
