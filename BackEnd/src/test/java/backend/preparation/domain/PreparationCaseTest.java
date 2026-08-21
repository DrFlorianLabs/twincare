package backend.preparation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PreparationCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-21T08:00:00Z");

    @Test
    void createsAnOrganizationOwnedPreparationEpisode() {
        PreparationCase preparationCase = newCase();

        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.CREATED);
        assertThat(preparationCase.organizationId()).isEqualTo(new OrganizationId("cabinet-a"));
        assertThat(preparationCase.patientReference()).isEqualTo(new PatientReference("patient-42"));
        assertThat(preparationCase.appointmentReference())
                .contains(new AppointmentReference("appointment-7"));
        assertThat(preparationCase.patientReason()).isEmpty();
        assertThat(preparationCase.releaseDomainEvents())
                .containsExactly(new PreparationCaseCreated(
                        preparationCase.id(), preparationCase.organizationId(), CREATED_AT));
    }

    @Test
    void followsTheMinimalPreparationLifecycle() {
        PreparationCase preparationCase = newCase();
        preparationCase.releaseDomainEvents();

        preparationCase.startPatientPreparation(atMinute(1));
        preparationCase.recordPatientReason("Fatigue depuis trois semaines", atMinute(2));
        preparationCase.markReadyForReview(atMinute(3));
        preparationCase.markReadyForConsultation(atMinute(4));
        preparationCase.close(atMinute(5));

        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.CLOSED);
        assertThat(preparationCase.patientReason()).get()
                .extracting(PatientStatement::provenance)
                .isEqualTo(ProvenanceType.PATIENT_DECLARED);
        assertThat(preparationCase.releaseDomainEvents()).containsExactly(
                new PreparationCaseStatusChanged(
                        preparationCase.id(), preparationCase.organizationId(),
                        PreparationCaseStatus.CREATED, PreparationCaseStatus.PATIENT_STARTED, atMinute(1)),
                new PreparationCaseStatusChanged(
                        preparationCase.id(), preparationCase.organizationId(),
                        PreparationCaseStatus.PATIENT_STARTED, PreparationCaseStatus.COLLECTING, atMinute(2)),
                new PatientReasonRecorded(
                        preparationCase.id(), preparationCase.organizationId(),
                        ProvenanceType.PATIENT_DECLARED, atMinute(2)),
                new PreparationCaseStatusChanged(
                        preparationCase.id(), preparationCase.organizationId(),
                        PreparationCaseStatus.COLLECTING, PreparationCaseStatus.READY_FOR_REVIEW, atMinute(3)),
                new PreparationCaseStatusChanged(
                        preparationCase.id(), preparationCase.organizationId(),
                        PreparationCaseStatus.READY_FOR_REVIEW,
                        PreparationCaseStatus.READY_FOR_CONSULTATION, atMinute(4)),
                new PreparationCaseStatusChanged(
                        preparationCase.id(), preparationCase.organizationId(),
                        PreparationCaseStatus.READY_FOR_CONSULTATION, PreparationCaseStatus.CLOSED, atMinute(5)));
    }

    @Test
    void routesAReviewBackToCollectionWhenActionIsNeeded() {
        PreparationCase preparationCase = collectingCase();
        preparationCase.markReadyForReview(atMinute(3));
        preparationCase.markNeedsAction(atMinute(4));

        preparationCase.recordPatientReason("Texte patient complété", atMinute(5));

        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.COLLECTING);
        assertThat(preparationCase.patientReason()).get()
                .extracting(PatientStatement::text)
                .isEqualTo("Texte patient complété");
    }

    @Test
    void rejectsClosingBeforeTheCaseIsReadyForConsultation() {
        PreparationCase preparationCase = collectingCase();

        assertThatThrownBy(() -> preparationCase.close(atMinute(3)))
                .isInstanceOf(InvalidPreparationCaseTransitionException.class)
                .hasMessageContaining("COLLECTING -> CLOSED");
    }

    @Test
    void requiresAReasonBeforeProfessionalReview() {
        PreparationCase preparationCase = newCase();
        preparationCase.startPatientPreparation(atMinute(1));

        assertThatThrownBy(() -> preparationCase.markReadyForReview(atMinute(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A patient reason is required before review");
    }

    @Test
    void keepsTerminalCasesImmutable() {
        for (PreparationCaseStatus terminal : EnumSet.of(
                PreparationCaseStatus.CLOSED,
                PreparationCaseStatus.EXPIRED,
                PreparationCaseStatus.CANCELLED)) {
            PreparationCase preparationCase = restoredCase(terminal);

            assertThat(preparationCase.status().isTerminal()).isTrue();
            assertThatThrownBy(() -> preparationCase.startPatientPreparation(atMinute(6)))
                    .isInstanceOf(InvalidPreparationCaseTransitionException.class);
        }
    }

    @Test
    void rejectsOutOfOrderEvents() {
        PreparationCase preparationCase = newCase();
        preparationCase.startPatientPreparation(atMinute(2));

        assertThatThrownBy(() -> preparationCase.recordPatientReason("Texte", atMinute(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsBlankAndOversizedPatientStatementsWithoutEchoingTheirContent() {
        assertThatThrownBy(() -> PatientStatement.declaredByPatient(" ", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patient statement is required");

        String oversized = "x".repeat(PatientStatement.MAX_LENGTH + 1);
        assertThatThrownBy(() -> PatientStatement.declaredByPatient(oversized, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patient statement exceeds the allowed length")
                .hasMessageNotContaining(oversized);
    }

    @Test
    void redactsPatientTextFromTheStatementStringRepresentation() {
        PatientStatement statement = PatientStatement.declaredByPatient(
                "Contenu de santé synthétique",
                CREATED_AT);

        assertThat(statement.toString())
                .contains("<redacted>")
                .doesNotContain("Contenu de santé synthétique");
    }

    @Test
    void rejectsMissingOwnershipIdentifiers() {
        assertThatThrownBy(() -> new OrganizationId(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientReference(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAConsultationReferenceOrDeadline() {
        assertThatThrownBy(() -> PreparationCase.create(
                new PreparationCaseId(UUID.randomUUID()),
                new OrganizationId("cabinet-a"),
                new PatientReference("patient-42"),
                Optional.empty(),
                Optional.empty(),
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appointment reference or consultation deadline");

        PreparationCase deadlineOnly = PreparationCase.create(
                new PreparationCaseId(UUID.randomUUID()),
                new OrganizationId("cabinet-a"),
                new PatientReference("patient-42"),
                Optional.empty(),
                Optional.of(atMinute(30)),
                CREATED_AT);

        assertThat(deadlineOnly.scheduledAt()).contains(atMinute(30));
    }

    @Test
    void doesNotAllowNeedsActionToBeClearedWithoutNewInput() {
        PreparationCase preparationCase = collectingCase();
        preparationCase.markReadyForReview(atMinute(3));
        preparationCase.markNeedsAction(atMinute(4));
        preparationCase.releaseDomainEvents();

        assertThatThrownBy(() -> preparationCase.markReadyForReview(atMinute(5)))
                .isInstanceOf(InvalidPreparationCaseTransitionException.class)
                .hasMessageContaining("NEEDS_ACTION -> READY_FOR_REVIEW");
        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.NEEDS_ACTION);
        assertThat(preparationCase.updatedAt()).isEqualTo(atMinute(4));
        assertThat(preparationCase.releaseDomainEvents()).isEmpty();

        preparationCase.recordPatientReason("Information demandée ajoutée", atMinute(5));
        preparationCase.markReadyForReview(atMinute(6));
        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.READY_FOR_REVIEW);
    }

    @Test
    void restoresPersistedStateWithoutEmittingNewEvents() {
        PatientStatement statement = PatientStatement.declaredByPatient("Motif synthétique", atMinute(2));

        PreparationCase restored = PreparationCase.restore(
                new PreparationCaseId(UUID.fromString("02571a52-dd7f-4e69-bc55-0a59ad2a1daa")),
                new OrganizationId("cabinet-a"),
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.of(Instant.parse("2026-08-22T08:00:00Z")),
                CREATED_AT,
                PreparationCaseStatus.READY_FOR_REVIEW,
                Optional.of(statement),
                atMinute(3));

        assertThat(restored.status()).isEqualTo(PreparationCaseStatus.READY_FOR_REVIEW);
        assertThat(restored.patientReason()).contains(statement);
        assertThat(restored.releaseDomainEvents()).isEmpty();
    }

    @Test
    void rejectsInconsistentRestoredState() {
        assertThatThrownBy(() -> PreparationCase.restore(
                new PreparationCaseId(UUID.randomUUID()),
                new OrganizationId("cabinet-a"),
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.empty(),
                CREATED_AT,
                PreparationCaseStatus.READY_FOR_REVIEW,
                Optional.empty(),
                atMinute(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a patient reason");

        PatientStatement futureStatement = PatientStatement.declaredByPatient("Motif", atMinute(4));
        assertThatThrownBy(() -> PreparationCase.restore(
                new PreparationCaseId(UUID.randomUUID()),
                new OrganizationId("cabinet-a"),
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.empty(),
                CREATED_AT,
                PreparationCaseStatus.COLLECTING,
                Optional.of(futureStatement),
                atMinute(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the case timeline");
    }

    @Test
    void leavesTheAggregateUntouchedAfterARejectedTransition() {
        PreparationCase preparationCase = collectingCase();
        preparationCase.releaseDomainEvents();
        Instant previousUpdate = preparationCase.updatedAt();

        assertThatThrownBy(() -> preparationCase.close(atMinute(3)))
                .isInstanceOf(InvalidPreparationCaseTransitionException.class);

        assertThat(preparationCase.status()).isEqualTo(PreparationCaseStatus.COLLECTING);
        assertThat(preparationCase.updatedAt()).isEqualTo(previousUpdate);
        assertThat(preparationCase.releaseDomainEvents()).isEmpty();
    }

    @Test
    void definesTheCompleteTransitionMatrix() {
        Map<PreparationCaseStatus, Set<PreparationCaseStatus>> expected = new EnumMap<>(
                PreparationCaseStatus.class);
        expected.put(PreparationCaseStatus.CREATED, EnumSet.of(
                PreparationCaseStatus.PATIENT_STARTED,
                PreparationCaseStatus.EXPIRED,
                PreparationCaseStatus.CANCELLED));
        expected.put(PreparationCaseStatus.PATIENT_STARTED, EnumSet.of(
                PreparationCaseStatus.COLLECTING,
                PreparationCaseStatus.EXPIRED,
                PreparationCaseStatus.CANCELLED));
        expected.put(PreparationCaseStatus.COLLECTING, EnumSet.of(
                PreparationCaseStatus.READY_FOR_REVIEW,
                PreparationCaseStatus.NEEDS_ACTION,
                PreparationCaseStatus.EXPIRED,
                PreparationCaseStatus.CANCELLED));
        expected.put(PreparationCaseStatus.READY_FOR_REVIEW, EnumSet.of(
                PreparationCaseStatus.NEEDS_ACTION,
                PreparationCaseStatus.READY_FOR_CONSULTATION,
                PreparationCaseStatus.CANCELLED));
        expected.put(PreparationCaseStatus.NEEDS_ACTION, EnumSet.of(
                PreparationCaseStatus.COLLECTING,
                PreparationCaseStatus.CANCELLED));
        expected.put(PreparationCaseStatus.READY_FOR_CONSULTATION, EnumSet.of(
                PreparationCaseStatus.CLOSED,
                PreparationCaseStatus.CANCELLED));
        expected.put(PreparationCaseStatus.CLOSED, EnumSet.noneOf(PreparationCaseStatus.class));
        expected.put(PreparationCaseStatus.EXPIRED, EnumSet.noneOf(PreparationCaseStatus.class));
        expected.put(PreparationCaseStatus.CANCELLED, EnumSet.noneOf(PreparationCaseStatus.class));

        for (PreparationCaseStatus current : PreparationCaseStatus.values()) {
            for (PreparationCaseStatus target : PreparationCaseStatus.values()) {
                assertThat(current.canTransitionTo(target))
                        .as("%s -> %s", current, target)
                        .isEqualTo(expected.get(current).contains(target));
            }
        }
    }

    @Test
    void redactsAndBoundsCorrelatableReferences() {
        PatientReference patient = new PatientReference("patient-42");
        AppointmentReference appointment = new AppointmentReference("appointment-7");

        assertThat(patient.toString()).doesNotContain("patient-42").contains("<redacted>");
        assertThat(appointment.toString()).doesNotContain("appointment-7").contains("<redacted>");
        assertThatThrownBy(() -> new PatientReference("x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("x".repeat(129));
        assertThatThrownBy(() -> new AppointmentReference("line\nbreak"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PreparationCase collectingCase() {
        PreparationCase preparationCase = newCase();
        preparationCase.startPatientPreparation(atMinute(1));
        preparationCase.recordPatientReason("Demande patient synthétique", atMinute(2));
        return preparationCase;
    }

    private PreparationCase newCase() {
        return PreparationCase.create(
                new PreparationCaseId(UUID.fromString("02571a52-dd7f-4e69-bc55-0a59ad2a1daa")),
                new OrganizationId("cabinet-a"),
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.of(Instant.parse("2026-08-22T08:00:00Z")),
                CREATED_AT);
    }

    private PreparationCase restoredCase(PreparationCaseStatus status) {
        boolean reasonRequired = status == PreparationCaseStatus.COLLECTING
                || status == PreparationCaseStatus.READY_FOR_REVIEW
                || status == PreparationCaseStatus.NEEDS_ACTION
                || status == PreparationCaseStatus.READY_FOR_CONSULTATION
                || status == PreparationCaseStatus.CLOSED;
        return PreparationCase.restore(
                new PreparationCaseId(UUID.fromString("02571a52-dd7f-4e69-bc55-0a59ad2a1daa")),
                new OrganizationId("cabinet-a"),
                new PatientReference("patient-42"),
                Optional.of(new AppointmentReference("appointment-7")),
                Optional.of(Instant.parse("2026-08-22T08:00:00Z")),
                CREATED_AT,
                status,
                reasonRequired
                        ? Optional.of(PatientStatement.declaredByPatient("Motif synthétique", atMinute(2)))
                        : Optional.empty(),
                atMinute(5));
    }

    private Instant atMinute(long minute) {
        return CREATED_AT.plus(minute, ChronoUnit.MINUTES);
    }
}
