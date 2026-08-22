package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.security.JwtService;
import backend.security.UtilisateurSecurite;

@SpringBootTest
@AutoConfigureMockMvc
class ProfessionalPreparationCommandApiIntegrationTest {

    private static final String ORGANIZATION_A = "cabinet-a";
    private static final String ORGANIZATION_B = "cabinet-b";
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppUtilisateurRepository utilisateurRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private AppUtilisateur physician;
    private AppUtilisateur assistant;

    @BeforeEach
    void setUp() {
        clearPreparationData();
        physician = createUser("task-physician@synthetic.invalid", Role.MEDECIN);
        assistant = createUser("task-assistant@synthetic.invalid", Role.ASSISTANT_MEDICAL);
        addMembership(ORGANIZATION_A, physician, "CLINICIAN", true);
        addMembership(ORGANIZATION_A, assistant, "MEDICAL_ASSISTANT", true);
    }

    @AfterEach
    void tearDown() {
        clearPreparationData();
    }

    @Test
    void humanTaskThenClinicianConfirmationFormsAnIdempotentRevisionCheckedWorkflow() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", "Synthetic exact reason");
        UUID requestId = UUID.randomUUID();
        String createBody = """
                {"requestId":"%s","label":"Retrieve synthetic result","assigneeUserId":%d}
                """.formatted(requestId, assistant.getId());

        String taskId = objectMapper.readTree(mockMvc.perform(post(tasksEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(assistant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assigneeUserId").value(assistant.getId()))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post(tasksEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(assistant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_task WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isOne();

        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("NEEDS_ACTION"))
                .andExpect(jsonPath("$.readiness.state").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.openTaskCount").value(1))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.tasks[0].label").value("Retrieve synthetic result"))
                .andExpect(jsonPath("$.readyConfirmation.confirmed").value(false));

        mockMvc.perform(put(readyEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        mockMvc.perform(put(taskStatusEndpoint(ORGANIZATION_A, caseId, taskId))
                        .header("Authorization", bearerToken(assistant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(put(readyEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put(readyEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("READY_FOR_CONSULTATION"))
                .andExpect(jsonPath("$.readiness.state").value("READY"))
                .andExpect(jsonPath("$.reviewedCaseRevision").value(2));

        mockMvc.perform(put(readyEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedByUserId").value(physician.getId()));

        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("READY_FOR_CONSULTATION"))
                .andExpect(jsonPath("$.readiness.state").value("READY"))
                .andExpect(jsonPath("$.openTaskCount").value(0))
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.readyConfirmation.confirmed").value(true))
                .andExpect(jsonPath("$.readyConfirmation.reviewedCaseRevision").value(2));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_ready_confirmation WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isOne();
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT event_type
                FROM preparation_audit_event
                WHERE preparation_case_id = ?
                ORDER BY event_sequence
                """,
                String.class,
                caseId)).containsExactly("TASK_CREATED", "TASK_COMPLETED", "READY_CONFIRMED");
    }

    @Test
    void assistantCannotConfirmAndUnrelatedAssigneeIsRejected() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", "Synthetic exact reason");
        AppUtilisateur outsider = createUser("task-outsider@synthetic.invalid", Role.ASSISTANT_MEDICAL);

        mockMvc.perform(put(readyEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(assistant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(tasksEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","label":"Synthetic task","assigneeUserId":%d}
                                """.formatted(UUID.randomUUID(), outsider.getId())))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preparation_task", Integer.class)).isZero();
    }

    @Test
    void tenantAndInputBoundariesFailClosed() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", "Synthetic exact reason");
        addMembership(ORGANIZATION_B, physician, "CLINICIAN", true);

        mockMvc.perform(post(tasksEndpoint(ORGANIZATION_B, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","label":"Synthetic task"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(tasksEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"label\":\"line one\\nline two\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(readyEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void taskLabelsAreNeverWrittenToApplicationLogs() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", "Synthetic exact reason");
        String marker = "synthetic-sensitive-task-" + UUID.randomUUID();
        var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        rootLogger.addAppender(appender);

        try {
            mockMvc.perform(post(tasksEndpoint(ORGANIZATION_A, caseId))
                            .header("Authorization", bearerToken(physician))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"requestId":"%s","label":"%s"}
                                    """.formatted(UUID.randomUUID(), marker)))
                    .andExpect(status().isCreated());
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(marker));
    }

    @RepeatedTest(3)
    void concurrentTaskCreationAndReadyConfirmationSerializeOnTheCase() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", "Synthetic exact reason");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var taskCreation = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post(tasksEndpoint(ORGANIZATION_A, caseId))
                                .header("Authorization", bearerToken(assistant))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"requestId":"%s","label":"Synthetic concurrent task"}
                                        """.formatted(UUID.randomUUID())))
                        .andReturn().getResponse().getStatus();
            });
            var confirmation = executor.submit(() -> {
                start.await();
                return mockMvc.perform(put(readyEndpoint(ORGANIZATION_A, caseId))
                                .header("Authorization", bearerToken(physician))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedRevision\":0}"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertThat(List.of(taskCreation.get(), confirmation.get()))
                    .contains(409)
                    .allMatch(code -> code == 200 || code == 201 || code == 409);
        }

        int taskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_task WHERE preparation_case_id = ?",
                Integer.class,
                caseId);
        int confirmationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_ready_confirmation WHERE preparation_case_id = ?",
                Integer.class,
                caseId);
        assertThat(taskCount + confirmationCount).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT event_sequence) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isOne();
    }

    private UUID insertCase(String organizationId, String status, String reason) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference, scheduled_at,
                    status, patient_reason, patient_reason_provenance, patient_reason_recorded_at,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                organizationId,
                "synthetic-patient-" + id,
                "synthetic-appointment-" + id,
                NOW.plusSeconds(3600),
                status,
                reason,
                "PATIENT_DECLARED",
                NOW,
                NOW,
                NOW,
                0L);
        return id;
    }

    private void clearPreparationData() {
        jdbcTemplate.update("DELETE FROM preparation_audit_event");
        jdbcTemplate.update("DELETE FROM preparation_ready_confirmation");
        jdbcTemplate.update("DELETE FROM preparation_task");
        jdbcTemplate.update("DELETE FROM preparation_attachment");
        jdbcTemplate.update("DELETE FROM patient_access_session");
        jdbcTemplate.update("DELETE FROM patient_invitation");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        utilisateurRepository.deleteAll();
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

    private void addMembership(String organizationId, AppUtilisateur user, String role, boolean active) {
        jdbcTemplate.update(
                """
                INSERT INTO organization_membership (
                    id, organization_id, user_id, membership_role, active, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                organizationId,
                user.getId(),
                role,
                active,
                NOW);
    }

    private String tasksEndpoint(String organizationId, UUID caseId) {
        return caseEndpoint(organizationId, caseId) + "/tasks";
    }

    private String taskStatusEndpoint(String organizationId, UUID caseId, String taskId) {
        return tasksEndpoint(organizationId, caseId) + "/" + taskId + "/status";
    }

    private String readyEndpoint(String organizationId, UUID caseId) {
        return caseEndpoint(organizationId, caseId) + "/ready-confirmation";
    }

    private String viewEndpoint(String organizationId, UUID caseId) {
        return caseEndpoint(organizationId, caseId) + "/preparation-view";
    }

    private String caseEndpoint(String organizationId, UUID caseId) {
        return "/api/v1/organizations/" + organizationId + "/preparation-cases/" + caseId;
    }

    private String bearerToken(AppUtilisateur user) {
        UtilisateurSecurite principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }
}
