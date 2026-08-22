package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.GetPatientReasonCommand;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.PatientReasonResult;
import backend.preparation.application.port.out.PatientReasonLookup;
import backend.preparation.application.port.out.PatientReasonSnapshot;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCaseId;

class GetPatientReasonServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T09:00:00Z");
    private static final PatientAccessGrant GRANT = new PatientAccessGrant(
            UUID.fromString("291127c0-f81c-411d-8d63-61f259bfc89b"),
            new PatientInvitationId(UUID.fromString("7a2814db-a1c6-4b02-908f-e04c2c104613")),
            new OrganizationId("synthetic-clinic"),
            new PreparationCaseId(UUID.fromString("bde8e553-6925-48f3-9e0e-62937c97bba4")),
            NOW.plusSeconds(3_600));

    @Test
    void returnsThePatientDeclaredReasonFromTheServerDerivedScope() {
        var capturedLookup = new AtomicReference<PatientReasonLookup>();
        var statement = PatientStatement.declaredByPatient("Motif synthétique", NOW.minusSeconds(60));
        var service = new GetPatientReasonService(lookup -> {
            capturedLookup.set(lookup);
            return Optional.of(new PatientReasonSnapshot(Optional.of(statement)));
        }, fixedClock());

        var result = service.get(new GetPatientReasonCommand(GRANT));

        assertThat(result.reason()).containsSame(statement);
        assertThat(capturedLookup.get()).satisfies(lookup -> {
            assertThat(lookup.sessionId()).isEqualTo(GRANT.sessionId());
            assertThat(lookup.invitationId()).isEqualTo(GRANT.invitationId());
            assertThat(lookup.organizationId()).isEqualTo(GRANT.organizationId());
            assertThat(lookup.preparationCaseId()).isEqualTo(GRANT.preparationCaseId());
            assertThat(lookup.sessionExpiresAt()).isEqualTo(GRANT.expiresAt());
            assertThat(lookup.readAt()).isEqualTo(NOW);
        });
    }

    @Test
    void representsAnActiveCaseWithoutAReasonExplicitly() {
        var service = new GetPatientReasonService(
                lookup -> Optional.of(new PatientReasonSnapshot(Optional.empty())),
                fixedClock());

        assertThat(service.get(new GetPatientReasonCommand(GRANT)).reason()).isEmpty();
    }

    @Test
    void unavailableOrMissingScopeFailsClosedWithoutExposingTechnicalValues() {
        var service = new GetPatientReasonService(lookup -> Optional.empty(), fixedClock());

        assertThatThrownBy(() -> service.get(new GetPatientReasonCommand(GRANT)))
                .isInstanceOf(PatientSessionUnavailableException.class)
                .hasMessageNotContaining(GRANT.sessionId().toString())
                .hasMessageNotContaining(GRANT.preparationCaseId().value().toString());
        assertThatThrownBy(() -> service.get(null))
                .isInstanceOf(PatientSessionUnavailableException.class);
        assertThatThrownBy(() -> service.get(new GetPatientReasonCommand(null)))
                .isInstanceOf(PatientSessionUnavailableException.class);
    }

    @Test
    void diagnosticStringsRedactClinicalAndTechnicalScope() {
        String marker = "SYNTHETIC-PATIENT-REASON-DO-NOT-LOG";
        var result = new PatientReasonResult(Optional.of(
                PatientStatement.declaredByPatient(marker, NOW)));
        var lookup = new PatientReasonLookup(
                GRANT.sessionId(),
                GRANT.invitationId(),
                GRANT.organizationId(),
                GRANT.preparationCaseId(),
                GRANT.expiresAt(),
                NOW);

        assertThat(result.toString()).doesNotContain(marker);
        assertThat(new GetPatientReasonCommand(GRANT).toString())
                .doesNotContain(GRANT.sessionId().toString(), GRANT.organizationId().value());
        assertThat(lookup.toString())
                .doesNotContain(GRANT.sessionId().toString(), GRANT.organizationId().value());
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
