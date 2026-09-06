package backend.clinicalcontext.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.security.JwtService;
import backend.security.UtilisateurSecurite;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalContextApiSecurityIntegrationTest {

    private static final String ORGANIZATION_A = "clinical-context-org-a";
    private static final String ORGANIZATION_B = "clinical-context-org-b";
    private static final Instant CREATED_AT = Instant.parse("2026-08-28T07:00:00Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-08-28T07:10:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AppUtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;

    private AppUtilisateur physician;
    private AppUtilisateur assistant;
    private AppUtilisateur administrator;

    @BeforeEach
    void setUp() {
        clearData();
        utilisateurRepository.deleteAll();
        physician = createUser("context-physician@synthetic.invalid", Role.MEDECIN);
        assistant = createUser("context-assistant@synthetic.invalid", Role.ASSISTANT_MEDICAL);
        administrator = createUser("context-admin@synthetic.invalid", Role.ADMIN);
        addMembership(ORGANIZATION_A, physician, "CLINICIAN");
        addMembership(ORGANIZATION_A, assistant, "MEDICAL_ASSISTANT");
        addMembership(ORGANIZATION_A, administrator, "ORGANIZATION_ADMIN");
    }

    @AfterEach
    void tearDown() {
        clearData();
        utilisateurRepository.deleteAll();
    }

    @Test
    void professionalReadsAPortableMinimalContextWithExplicitProvenanceAndTime() throws Exception {
        UUID caseId = insertCaseWithReason("Motif synthétique de consultation");
        UUID factId = currentFactId(caseId);

        mockMvc.perform(get(contextEndpoint(ORGANIZATION_A, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.schemaVersion").value("twincare.clinical-context.v1"))
                .andExpect(jsonPath("$.contextId").value(caseId.toString()))
                .andExpect(jsonPath("$.preparationStatus").value("COLLECTING"))
                .andExpect(jsonPath("$.preparationRevision").value(0))
                .andExpect(jsonPath("$.readiness").value("COMPLETE"))
                .andExpect(jsonPath("$.preparationItems[0].code").value("CONSULTATION_REASON"))
                .andExpect(jsonPath("$.preparationItems[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.preparationItems[1].requirement").value("OPTIONAL"))
                .andExpect(jsonPath("$.facts[0].id").value(factId.toString()))
                .andExpect(jsonPath("$.facts[0].kind").value("CONSULTATION_REASON"))
                .andExpect(jsonPath("$.facts[0].value.text").value("Motif synthétique de consultation"))
                .andExpect(jsonPath("$.facts[0].provenance.type").value("PATIENT_DECLARED"))
                .andExpect(jsonPath("$.facts[0].certainty").value("NOT_ASSESSED"))
                .andExpect(jsonPath("$.facts[0].temporal.recordedAt").value(RECORDED_AT.toString()))
                .andExpect(jsonPath("$.facts[0].temporal.effectiveFrom").isEmpty())
                .andExpect(jsonPath("$.facts[0].lifecycle.status").value("CURRENT"))
                .andExpect(jsonPath("$.facts[0].review.status").value("UNREVIEWED"))
                .andExpect(jsonPath("$.documents").isEmpty())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.patientReference").doesNotExist())
                .andExpect(jsonPath("$.facts[0].reviewerReference").doesNotExist());
    }

    @Test
    void clinicianReviewIsPersistedAndIdenticalRetryIsIdempotent() throws Exception {
        UUID caseId = insertCaseWithReason("Motif synthétique à confirmer");
        UUID factId = currentFactId(caseId);
        String endpoint = reviewEndpoint(ORGANIZATION_A, caseId, factId);

        String firstResponse = mockMvc.perform(put(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.factId").value(factId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long firstVersion = clinicalFactVersion(factId);
        long firstCaseRevision = preparationCaseRevision(caseId);

        mockMvc.perform(put(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .isEqualTo(firstResponse));

        assertThat(firstCaseRevision).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class, caseId)).isEqualTo(1);
        var event = jdbcTemplate.queryForMap(
                "SELECT * FROM preparation_audit_event WHERE preparation_case_id = ?", caseId);
        assertThat(event.get("event_type")).isEqualTo("CLINICAL_FACT_CONFIRMED");
        assertThat(event.get("actor_kind")).isEqualTo("STAFF");
        assertThat(((Number) event.get("actor_user_id")).longValue()).isEqualTo(physician.getId());
        assertThat(((Number) event.get("case_revision")).longValue()).isEqualTo(firstCaseRevision);
        assertThat(event.get("subject_type")).isEqualTo("CLINICAL_FACT");
        assertThat(event.get("subject_id")).isEqualTo(factId);
        assertThat(event.values()).doesNotContain("Motif synthétique à confirmer");
        mockMvc.perform(get(contextEndpoint(ORGANIZATION_A, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preparationRevision").value(firstCaseRevision))
                .andExpect(jsonPath("$.facts[0].review.status").value("CONFIRMED"));

        assertThat(clinicalFactVersion(factId)).isEqualTo(firstVersion);
        assertThat(preparationCaseRevision(caseId)).isEqualTo(firstCaseRevision);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reviewer_reference FROM clinical_fact WHERE id = ?",
                String.class,
                factId)).isEqualTo("local-user:" + physician.getId());
    }

    @Test
    void factReviewIsBlockedAfterCaseClosure() throws Exception {
        UUID caseId = insertCaseWithReason("Motif synthétique clôturé");
        UUID factId = currentFactId(caseId);
        jdbcTemplate.update(
                "UPDATE preparation_case SET status = 'CLOSED' WHERE id = ?",
                caseId);

        mockMvc.perform(put(reviewEndpoint(ORGANIZATION_A, caseId, factId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINICAL_FACT_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_status FROM clinical_fact WHERE id = ?",
                String.class,
                factId)).isEqualTo("UNREVIEWED");
    }

    @Test
    void finalReviewCannotBeSilentlyReplacedAndRejectedReasonNeedsCorrection() throws Exception {
        UUID confirmedCase = insertCaseWithReason("Motif synthétique confirmé");
        UUID confirmedFact = currentFactId(confirmedCase);
        String confirmedEndpoint = reviewEndpoint(ORGANIZATION_A, confirmedCase, confirmedFact);
        mockMvc.perform(put(confirmedEndpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(confirmedEndpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINICAL_FACT_CONFLICT"));

        UUID rejectedCase = insertCaseWithReason("Motif synthétique à corriger");
        UUID rejectedFact = currentFactId(rejectedCase);
        mockMvc.perform(put(reviewEndpoint(ORGANIZATION_A, rejectedCase, rejectedFact))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(assistant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get(contextEndpoint(ORGANIZATION_A, rejectedCase))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness").value("NEEDS_CORRECTION"))
                .andExpect(jsonPath("$.preparationItems[0].status").value("NEEDS_CORRECTION"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM preparation_audit_event WHERE preparation_case_id = ?",
                String.class, rejectedCase)).isEqualTo("CLINICAL_FACT_REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM preparation_audit_event WHERE preparation_case_id = ?",
                Long.class, rejectedCase)).isEqualTo(assistant.getId());
    }

    @Test
    void organizationAdminCannotReadOrReviewClinicalContext() throws Exception {
        UUID caseId = insertCaseWithReason("Motif synthétique protégé");
        UUID factId = currentFactId(caseId);

        mockMvc.perform(get(contextEndpoint(ORGANIZATION_A, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(administrator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(reviewEndpoint(ORGANIZATION_A, caseId, factId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(administrator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_status FROM clinical_fact WHERE id = ?",
                String.class,
                factId)).isEqualTo("UNREVIEWED");
    }

    @Test
    void crossOrganizationPatientAndAnonymousAccessFailClosed() throws Exception {
        UUID caseId = insertCaseWithReason("Motif synthétique isolé");
        AppUtilisateur otherOrganizationClinician = createUser(
                "other-context-clinician@synthetic.invalid", Role.MEDECIN);
        addMembership(ORGANIZATION_B, otherOrganizationClinician, "CLINICIAN");
        AppUtilisateur patient = createUser("context-patient@synthetic.invalid", Role.PATIENT);

        mockMvc.perform(get(contextEndpoint(ORGANIZATION_A, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(otherOrganizationClinician)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(contextEndpoint(ORGANIZATION_A, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(patient)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(contextEndpoint(ORGANIZATION_A, caseId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(reviewEndpoint(ORGANIZATION_A, caseId, currentFactId(caseId)))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(otherOrganizationClinician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isForbidden());
        jdbcTemplate.update("UPDATE organization_membership SET active = FALSE WHERE user_id = ?",
                physician.getId());
        mockMvc.perform(put(reviewEndpoint(ORGANIZATION_A, caseId, currentFactId(caseId)))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_status FROM clinical_fact WHERE id = ?",
                String.class, currentFactId(caseId))).isEqualTo("UNREVIEWED");
    }

    @Test
    void invalidDecisionAndUnknownFactReturnControlledErrors() throws Exception {
        UUID caseId = insertCaseWithReason("Motif synthétique contrôlé");

        mockMvc.perform(put(reviewEndpoint(ORGANIZATION_A, caseId, currentFactId(caseId)))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"AUTO_APPROVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(put(reviewEndpoint(ORGANIZATION_A, caseId, UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_FACT_NOT_FOUND"));
    }


    @Test
    void readyContextRejectsNewReviewAndAllowsAnIdenticalConfirmedReplay() throws Exception {
        UUID caseId = insertCaseWithReason("Motif synthétique déjà prêt");
        UUID factId = currentFactId(caseId);
        String endpoint = reviewEndpoint(ORGANIZATION_A, caseId, factId);
        jdbcTemplate.update("UPDATE preparation_case SET status = 'READY_FOR_CONSULTATION' WHERE id = ?",
                caseId);

        mockMvc.perform(put(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isConflict());
        assertThat(clinicalFactVersion(factId)).isZero();
        assertThat(preparationCaseRevision(caseId)).isZero();

        jdbcTemplate.update("""
                UPDATE clinical_fact
                SET review_status = 'CONFIRMED', reviewed_at = ?, reviewer_reference = ?
                WHERE id = ?
                """, RECORDED_AT.plusSeconds(60), "local-user:" + physician.getId(), factId);
        mockMvc.perform(put(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedAt").value(RECORDED_AT.plusSeconds(60).toString()));
        mockMvc.perform(put(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isConflict());
        assertThat(clinicalFactVersion(factId)).isZero();
        assertThat(preparationCaseRevision(caseId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class, caseId)).isZero();
    }

    private UUID insertCaseWithReason(String reason) {
        UUID caseId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference,
                    status, patient_reason, patient_reason_provenance,
                    patient_reason_recorded_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'COLLECTING', ?, 'PATIENT_DECLARED', ?, ?, ?, 0)
                """,
                caseId,
                ORGANIZATION_A,
                "synthetic-patient-" + caseId,
                "synthetic-appointment-" + caseId,
                reason,
                RECORDED_AT,
                CREATED_AT,
                RECORDED_AT);
        UUID factId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO clinical_fact (
                    id, organization_id, preparation_case_id, fact_kind, text_value,
                    provenance, certainty, recorded_at, lifecycle_status, current_slot,
                    review_status, version
                ) VALUES (?, ?, ?, 'CONSULTATION_REASON', ?, 'PATIENT_DECLARED',
                          'NOT_ASSESSED', ?, 'CURRENT', 1, 'UNREVIEWED', 0)
                """,
                factId,
                ORGANIZATION_A,
                caseId,
                reason,
                RECORDED_AT);
        return caseId;
    }

    private UUID currentFactId(UUID caseId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM clinical_fact WHERE preparation_case_id = ? AND current_slot = 1",
                UUID.class,
                caseId);
    }

    private long clinicalFactVersion(UUID factId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM clinical_fact WHERE id = ?", Long.class, factId);
    }

    private long preparationCaseRevision(UUID caseId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM preparation_case WHERE id = ?",
                Long.class,
                caseId);
    }

    private String contextEndpoint(String organizationId, UUID caseId) {
        return "/api/v1/organizations/" + organizationId
                + "/preparation-cases/" + caseId + "/clinical-context";
    }

    private String reviewEndpoint(String organizationId, UUID caseId, UUID factId) {
        return contextEndpoint(organizationId, caseId) + "/facts/" + factId + "/review";
    }

    private void addMembership(String organizationId, AppUtilisateur user, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO organization_membership (
                    id, organization_id, user_id, membership_role, active, created_at
                ) VALUES (?, ?, ?, ?, TRUE, ?)
                """,
                UUID.randomUUID(),
                organizationId,
                user.getId(),
                role,
                CREATED_AT);
    }

    private AppUtilisateur createUser(String email, Role role) {
        return utilisateurRepository.save(new AppUtilisateur(
                "Synthetic",
                "User",
                LocalDate.of(1990, 1, 1),
                email,
                passwordEncoder.encode("synthetic-password"),
                role,
                Sexe.NON_PRECISE));
    }

    private String bearerToken(AppUtilisateur user) {
        var principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }

    private void clearData() {
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
    }
}
