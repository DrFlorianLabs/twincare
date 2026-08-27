package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.security.JwtService;
import backend.security.UtilisateurSecurite;

@SpringBootTest
@AutoConfigureMockMvc
class ProfessionalPreparationAuditApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final String ORGANIZATION_A = "audit-cabinet-a";
    private static final String ORGANIZATION_B = "audit-cabinet-b";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppUtilisateurRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private AppUtilisateur physician;
    private AppUtilisateur assistant;

    @BeforeEach
    void setUp() {
        clearData();
        addOrganization(ORGANIZATION_A, "Cabinet audit A");
        addOrganization(ORGANIZATION_B, "Cabinet audit B");
        physician = createUser("audit-physician@synthetic.invalid", Role.MEDECIN);
        assistant = createUser("audit-assistant@synthetic.invalid", Role.ASSISTANT_MEDICAL);
        addMembership(ORGANIZATION_A, physician, "CLINICIAN", true);
        addMembership(ORGANIZATION_B, physician, "CLINICIAN", true);
        addMembership(ORGANIZATION_A, assistant, "MEDICAL_ASSISTANT", true);
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void clinicianReadsAStableBoundedTimelineWithoutClinicalPayload() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A);
        insertAudit(caseId, 1, "PREPARATION_CREATED", 0, null, null);
        UUID taskId = UUID.randomUUID();
        insertAudit(caseId, 2, "TASK_CREATED", 1, "PREPARATION_TASK", taskId);
        insertAudit(caseId, 3, "READY_CONFIRMED", 2, null, null);

        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId)).param("limit", "2")
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sequence").value(3))
                .andExpect(jsonPath("$.items[0].type").value("READY_CONFIRMED"))
                .andExpect(jsonPath("$.items[0].actorKind").value("STAFF"))
                .andExpect(jsonPath("$.items[0].actorUserId").value(physician.getId()))
                .andExpect(jsonPath("$.items[1].subject.type").value("PREPARATION_TASK"))
                .andExpect(jsonPath("$.items[1].subject.id").value(taskId.toString()))
                .andExpect(jsonPath("$.nextBeforeSequence").value(2))
                .andExpect(jsonPath("$.completeFromCaseCreation").value(true))
                .andExpect(jsonPath("$.items[0].patientReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload").doesNotExist());

        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId))
                        .param("beforeSequence", "2")
                        .param("limit", "2")
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("PREPARATION_CREATED"))
                .andExpect(jsonPath("$.nextBeforeSequence").doesNotExist());
    }

    @Test
    void legacyCaseReportsIncompleteCoverageAndInvalidPaginationFailsClosed() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A);
        insertAudit(caseId, 1, "TASK_CREATED", 1, "PREPARATION_TASK", UUID.randomUUID());

        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeFromCaseCreation").value(false));
        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId)).param("limit", "51")
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId)).param("beforeSequence", "0")
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assistantAdministratorsAnonymousAndCrossTenantLookupsFailClosed() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A);
        AppUtilisateur administrator = createUser("audit-admin@synthetic.invalid", Role.ADMIN);
        addMembership(ORGANIZATION_A, administrator, "ORGANIZATION_ADMIN", true);

        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(assistant)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(administrator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(endpoint(ORGANIZATION_A, caseId)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get(endpoint(ORGANIZATION_B, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isNotFound());
    }

    @Test
    void newCaseCreationAndItsFirstAuditEventArePersistedTogether() throws Exception {
        String body = """
                {
                  "requestId":"74c8e2bb-45c9-4482-8103-0ec59bcf5040",
                  "patientReference":"synthetic-audit-patient",
                  "appointmentReference":"synthetic-audit-appointment"
                }
                """;
        String response = mockMvc.perform(post("/api/v1/organizations/" + ORGANIZATION_A + "/preparation-cases")
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID caseId = UUID.fromString(objectMapper.readTree(response).get("caseId").asText());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM preparation_audit_event WHERE preparation_case_id = ?",
                String.class,
                caseId)).isEqualTo("PREPARATION_CREATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM preparation_audit_event WHERE preparation_case_id = ?",
                Long.class,
                caseId)).isEqualTo(physician.getId());
    }

    private UUID insertCase(String organizationId) {
        UUID caseId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference, scheduled_at,
                    status, patient_reason, patient_reason_provenance, patient_reason_recorded_at,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                caseId, organizationId, "synthetic-" + caseId, "appointment-" + caseId,
                NOW.plusSeconds(3600), "COLLECTING", "synthetic reason", "PATIENT_DECLARED",
                NOW, NOW, NOW, 0L);
        return caseId;
    }

    private void insertAudit(
            UUID caseId,
            long sequence,
            String type,
            long caseRevision,
            String subjectType,
            UUID subjectId) {
        jdbcTemplate.update(
                """
                INSERT INTO preparation_audit_event (
                    event_id, organization_id, preparation_case_id, event_sequence,
                    event_type, event_version, actor_kind, actor_user_id,
                    occurred_at, case_revision, subject_type, subject_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), ORGANIZATION_A, caseId, sequence, type, 1, "STAFF",
                physician.getId(), NOW.plusSeconds(sequence), caseRevision, subjectType, subjectId);
    }

    private String endpoint(String organizationId, UUID caseId) {
        return "/api/v1/organizations/" + organizationId
                + "/preparation-cases/" + caseId + "/audit-events";
    }

    private void addOrganization(String id, String displayName) {
        jdbcTemplate.update(
                "INSERT INTO care_organization (id, display_name) VALUES (?, ?)",
                id, displayName);
    }

    private AppUtilisateur createUser(String email, Role role) {
        return users.save(new AppUtilisateur(
                "Synthetic", "Audit", LocalDate.of(1990, 1, 1), email,
                passwordEncoder.encode("synthetic-password"), role, Sexe.NON_PRECISE));
    }

    private void addMembership(String organizationId, AppUtilisateur user, String role, boolean active) {
        jdbcTemplate.update(
                """
                INSERT INTO organization_membership (
                    id, organization_id, user_id, membership_role, active, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), organizationId, user.getId(), role, active, NOW);
    }

    private String bearerToken(AppUtilisateur user) {
        var principal = new UtilisateurSecurite(
                user.getEmail(), user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }

    private void clearData() {
        jdbcTemplate.update("DELETE FROM preparation_audit_event");
        jdbcTemplate.update("DELETE FROM preparation_case_closure");
        jdbcTemplate.update("DELETE FROM preparation_ready_confirmation");
        jdbcTemplate.update("DELETE FROM preparation_task");
        jdbcTemplate.update("DELETE FROM preparation_attachment");
        jdbcTemplate.update("DELETE FROM patient_access_session");
        jdbcTemplate.update("DELETE FROM patient_invitation");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        jdbcTemplate.update("DELETE FROM care_organization");
        users.deleteAll();
    }
}
