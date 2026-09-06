package backend.demo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.clinicalcontext.application.port.in.ClinicalContextView;
import backend.clinicalcontext.application.port.out.AuthorizedClinicalReviewer;
import backend.clinicalcontext.application.port.in.GetClinicalContextQuery;
import backend.clinicalcontext.application.port.in.GetClinicalContextUseCase;
import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactCommand;
import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactUseCase;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactCommand;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactUseCase;
import backend.clinicalcontext.application.service.GetClinicalContextService;
import backend.clinicalcontext.application.service.ReviewClinicalFactService;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ClinicalReviewerReference;
import backend.clinicalcontext.domain.FactReviewDecision;
import backend.demo.adapter.config.DemoEnvironmentGuard;
import backend.exception.BadRequestException;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;
import backend.preparation.domain.PreparationAuditEvent;
import backend.preparation.domain.PreparationAuditEventType;

@Service
@Profile("demo")
public class DemoScenarioService {

    public static final OrganizationId DEMO_ORGANIZATION = new OrganizationId("demo-clinic");
    public static final PreparationCaseId DEMO_CASE_ID = new PreparationCaseId(
            UUID.fromString("00000000-0000-0000-0000-000000000006"));

    private static final Instant DEMO_CREATED_AT = Instant.parse("2026-01-15T08:00:00Z");
    private static final long DEMO_REVIEWER_ID = 1L;
    private static final Instant DEMO_STARTED_AT = Instant.parse("2026-01-15T08:01:00Z");
    private static final Instant DEMO_SCHEDULED_AT = Instant.parse("2099-01-15T10:00:00Z");

    private final DemoEnvironmentGuard environmentGuard;
    private final JdbcTemplate jdbcTemplate;
    private final PreparationCaseRepository preparationCases;
    private final PreparationAuditEventStore auditEvents;
    private final RecordPatientDeclaredReasonFactUseCase recordClinicalFact;
    private final GetClinicalContextUseCase contextReader;
    private final ReviewClinicalFactUseCase factReviewer;
    private final Clock clock;

    public DemoScenarioService(
            DemoEnvironmentGuard environmentGuard,
            JdbcTemplate jdbcTemplate,
            PreparationCaseRepository preparationCases,
            PreparationAuditEventStore auditEvents,
            backend.clinicalcontext.application.port.out.ClinicalFactRepository facts,
            PreparationAttachmentRepository attachments,
            RecordPatientDeclaredReasonFactUseCase recordClinicalFact,
            Clock clock) {
        this.environmentGuard = environmentGuard;
        this.jdbcTemplate = jdbcTemplate;
        this.preparationCases = preparationCases;
        this.auditEvents = auditEvents;
        this.recordClinicalFact = recordClinicalFact;
        this.clock = clock;
        this.contextReader = new GetClinicalContextService(
                owner -> requireDemoOwner(owner),
                preparationCases,
                facts,
                attachments,
                clock);
        this.factReviewer = new ReviewClinicalFactService(
                owner -> {
                    requireDemoOwner(owner);
                    return new AuthorizedClinicalReviewer(
                            new ClinicalReviewerReference("demo-clinician"), DEMO_REVIEWER_ID);
                },
                preparationCases,
                facts,
                auditEvents,
                clock);
    }

    @Transactional
    public ClinicalContextView reset() {
        environmentGuard.requireSafeDemoEnvironment();
        jdbcTemplate.update("DELETE FROM preparation_audit_event");
        jdbcTemplate.update("DELETE FROM preparation_case_closure");
        jdbcTemplate.update("DELETE FROM preparation_ready_confirmation");
        jdbcTemplate.update("DELETE FROM preparation_task");
        jdbcTemplate.update("DELETE FROM clinical_fact");
        jdbcTemplate.update("DELETE FROM preparation_attachment");
        jdbcTemplate.update("DELETE FROM patient_access_session");
        jdbcTemplate.update("DELETE FROM patient_invitation");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        jdbcTemplate.update("DELETE FROM app_utilisateur WHERE id = ?", DEMO_REVIEWER_ID);
        jdbcTemplate.update("DELETE FROM care_organization");
        jdbcTemplate.update(
                "INSERT INTO care_organization (id, display_name, created_at) VALUES (?, ?, ?)",
                DEMO_ORGANIZATION.value(),
                "Cabinet de démonstration",
                DEMO_CREATED_AT);

        // A synthetic membership keeps the real audit foreign keys active.
        // The deliberately invalid password hash cannot authenticate this identity.
        jdbcTemplate.update("""
                INSERT INTO app_utilisateur (
                    id, nom, prenom, date_naissance, email, mot_de_passe, role, sexe
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                DEMO_REVIEWER_ID, "Démonstration", "Médecin", java.time.LocalDate.of(1990, 1, 1),
                "demo-clinician@synthetic.invalid", "!disabled-demo-account", "MEDECIN", "NON_PRECISE");
        jdbcTemplate.update("""
                INSERT INTO organization_membership (
                    id, organization_id, user_id, membership_role, active, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                DEMO_ORGANIZATION.value(), DEMO_REVIEWER_ID, "CLINICIAN", true, DEMO_CREATED_AT);

        PreparationCase preparationCase = PreparationCase.create(
                DEMO_CASE_ID,
                DEMO_ORGANIZATION,
                new PatientReference("synthetic-demo-patient"),
                Optional.of(new AppointmentReference("synthetic-demo-appointment")),
                Optional.of(DEMO_SCHEDULED_AT),
                DEMO_CREATED_AT);
        preparationCase.startPatientPreparation(DEMO_STARTED_AT);
        preparationCases.add(preparationCase);
        return read();
    }

    @Transactional
    public ClinicalContextView read() {
        environmentGuard.requireSafeDemoEnvironment();
        return contextReader.get(new GetClinicalContextQuery(DEMO_ORGANIZATION, DEMO_CASE_ID));
    }

    @Transactional
    public ClinicalContextView recordPatientReason(String reason) {
        environmentGuard.requireSafeDemoEnvironment();
        PreparationCase preparationCase = preparationCases
                .findByIdForUpdate(DEMO_CASE_ID, DEMO_ORGANIZATION)
                .orElseThrow(() -> new IllegalStateException("Synthetic demo case is unavailable"));
        Instant now = clock.instant();
        try {
            if (preparationCase.recordPatientReason(reason, now)) {
                long persistedRevision = preparationCases.update(preparationCase);
                var statement = preparationCase.patientReason().orElseThrow();
                recordClinicalFact.record(new RecordPatientDeclaredReasonFactCommand(
                        new ClinicalContextId(preparationCase.id().value()),
                        new ClinicalContextOwner(preparationCase.organizationId().value()),
                        statement.text(),
                        statement.recordedAt()));
                auditEvents.append(PreparationAuditEvent.patient(
                        preparationCase.organizationId(),
                        preparationCase.id(),
                        PreparationAuditEventType.PATIENT_REASON_RECORDED,
                        now,
                        persistedRevision,
                        Optional.empty()));
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Motif fictif invalide");
        }
        return read();
    }

    @Transactional
    public ClinicalContextView reviewFact(UUID factId, FactReviewDecision decision) {
        environmentGuard.requireSafeDemoEnvironment();
        factReviewer.review(new ReviewClinicalFactCommand(
                DEMO_ORGANIZATION,
                DEMO_CASE_ID,
                new ClinicalFactId(factId),
                decision));
        return read();
    }

    private void requireDemoOrganization(OrganizationId organizationId) {
        environmentGuard.requireSafeDemoEnvironment();
        if (!DEMO_ORGANIZATION.equals(organizationId)) {
            throw new IllegalStateException("Demo access is limited to the synthetic organization");
        }
    }

    private void requireDemoOwner(ClinicalContextOwner owner) {
        environmentGuard.requireSafeDemoEnvironment();
        if (!DEMO_ORGANIZATION.value().equals(owner.value())) {
            throw new IllegalStateException("Demo review is limited to the synthetic organization");
        }
    }
}
