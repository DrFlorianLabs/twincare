package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.security.JwtService;
import backend.security.UtilisateurSecurite;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@AutoConfigureMockMvc
class PreparationCaseApiSecurityIntegrationTest {

    private static final String ORGANIZATION_A = "cabinet-a";
    private static final String ORGANIZATION_B = "cabinet-b";

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

    private AppUtilisateur physician;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM preparation_audit_event");
        jdbcTemplate.update("DELETE FROM preparation_case_closure");
        jdbcTemplate.update("DELETE FROM preparation_ready_confirmation");
        jdbcTemplate.update("DELETE FROM preparation_task");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        jdbcTemplate.update("DELETE FROM care_organization");
        utilisateurRepository.deleteAll();
        addOrganization(ORGANIZATION_A, "Synthetic practice A");
        addOrganization(ORGANIZATION_B, "Synthetic practice B");
        physician = createUser("physician@synthetic.invalid", Role.MEDECIN);
        addMembership(ORGANIZATION_A, physician, "CLINICIAN", true);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM preparation_audit_event");
        jdbcTemplate.update("DELETE FROM preparation_case_closure");
        jdbcTemplate.update("DELETE FROM preparation_ready_confirmation");
        jdbcTemplate.update("DELETE FROM preparation_task");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        jdbcTemplate.update("DELETE FROM care_organization");
        utilisateurRepository.deleteAll();
    }

    @Test
    void organizationMemberCanCreateAMinimalCase() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "dd7f8e86-9086-4a7a-a3f9-f4052df7122f",
                                  "patientReference": "synthetic-patient-42",
                                  "appointmentReference": "synthetic-appointment-7"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/organizations/cabinet-a/preparation-cases/")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/preparation-view")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.caseId").isString())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.patientReference").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist());

        assertThat(countCases(ORGANIZATION_A)).isOne();
    }

    @Test
    void membershipInAnotherOrganizationDoesNotGrantAccess() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_B))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());

        assertThat(countCases(ORGANIZATION_B)).isZero();
    }

    @Test
    void inactiveMembershipFailsClosed() throws Exception {
        jdbcTemplate.update("UPDATE organization_membership SET active = FALSE WHERE user_id = ?", physician.getId());

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());

        assertThat(countCases(ORGANIZATION_A)).isZero();
    }

    @Test
    void medicalAssistantCapabilityAllowsCreationButOrganizationAdministratorMembershipDoesNot() throws Exception {
        AppUtilisateur assistant = createUser("assistant@synthetic.invalid", Role.ASSISTANT_MEDICAL);
        addMembership(ORGANIZATION_A, assistant, "MEDICAL_ASSISTANT", true);

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(assistant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated());

        jdbcTemplate.update("DELETE FROM preparation_audit_event");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update(
                "UPDATE organization_membership SET membership_role = 'ORGANIZATION_ADMIN' WHERE user_id = ?",
                physician.getId());

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());

        assertThat(countCases(ORGANIZATION_A)).isZero();
    }

    @Test
    void patientAndAnonymousRequestsAreRejected() throws Exception {
        AppUtilisateur patient = createUser("patient@synthetic.invalid", Role.PATIENT);
        addMembership(ORGANIZATION_A, patient, "CLINICIAN", true);

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
        assertThat(countCases(ORGANIZATION_A)).isZero();
    }

    @Test
    void requiresAnAppointmentReferenceOrConsultationDeadline() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId":"37bdd959-c954-4a31-bdf1-4cd2b3a842ad",
                                  "patientReference":"synthetic-patient-42"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(countCases(ORGANIZATION_A)).isZero();
    }

    @Test
    void missingOrMalformedRequestIdentifierIsRejected() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientReference":"synthetic-patient-42",
                                  "appointmentReference":"synthetic-appointment-7"
                                }
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId":"not-a-uuid",
                                  "patientReference":"synthetic-patient-42",
                                  "appointmentReference":"synthetic-appointment-7"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(countCases(ORGANIZATION_A)).isZero();
    }

    @Test
    void rejectsUnknownAuthorityFieldsFromTheClientBody() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "29f5bc64-3ccc-4629-a6e4-07236564303d",
                                  "organizationId": "cabinet-b",
                                  "patientReference": "synthetic-patient-42",
                                  "scheduledAt": "2026-08-22T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(countCases(ORGANIZATION_A)).isZero();
        assertThat(countCases(ORGANIZATION_B)).isZero();
    }

    @Test
    void sameRequestAndPayloadReplaysTheOriginalCaseWithoutSecondAudit() throws Exception {
        UUID requestId = UUID.fromString("1abcde4d-abaf-4830-8473-d3c645461dd6");
        String request = validRequest(requestId);
        String firstCaseId = mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/organizations/cabinet-a/preparation-cases/")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/preparation-view")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.caseId").value(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(firstCaseId).get("caseId").asText()));

        assertThat(countCases(ORGANIZATION_A)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE event_type = 'PREPARATION_CREATED'",
                Integer.class)).isOne();
    }

    @Test
    void sameRequestWithDifferentPayloadReturnsControlledConflict() throws Exception {
        UUID requestId = UUID.fromString("7422e310-8ee5-4be8-8ef4-81754cb31a2a");
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(requestId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "patientReference": "synthetic-patient-different",
                                  "appointmentReference": "synthetic-appointment-different"
                                }
                                """.formatted(requestId)))
                .andExpect(status().isConflict());

        assertThat(countCases(ORGANIZATION_A)).isOne();
    }

    @RepeatedTest(5)
    void concurrentSameRequestCreatesOneCaseAndOneAuditEvent() throws Exception {
        UUID requestId = UUID.fromString("d875d476-c350-4bfa-96e9-c971a9da57e2");
        String marker = "synthetic-concurrent-marker-" + UUID.randomUUID();
        String request = """
                {
                  "requestId": "%s",
                  "patientReference": "%s-patient",
                  "appointmentReference": "%s-appointment"
                }
                """.formatted(requestId, marker, marker);
        String authorization = bearerToken(physician);
        var start = new CountDownLatch(1);
        var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        rootLogger.addAppender(appender);

        try {
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> creationStatusAfter(start, authorization, request));
                var second = executor.submit(() -> creationStatusAfter(start, authorization, request));
                start.countDown();

                assertThat(List.of(first.get(), second.get()))
                        .containsExactlyInAnyOrder(200, 201);
            }
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(countCases(ORGANIZATION_A)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE event_type = 'PREPARATION_CREATED'",
                Integer.class)).isOne();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(marker));
    }

    @Test
    void duplicateAppointmentWithDifferentRequestReturnsControlledConflict() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(UUID.randomUUID())))
                .andExpect(status().isCreated());

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(UUID.randomUUID())))
                .andExpect(status().isConflict());

        assertThat(countCases(ORGANIZATION_A)).isOne();
    }

    @Test
    void duplicatePatientAndDeadlineWithoutAppointmentReturnsConflict() throws Exception {
        String deadlineOnlyRequest = """
                {
                  "requestId": "89a4195d-dc72-478d-85a6-068821f6989c",
                  "patientReference": "synthetic-patient-42",
                  "scheduledAt": "2099-08-22T10:00:00Z"
                }
                """;

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deadlineOnlyRequest))
                .andExpect(status().isCreated());
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deadlineOnlyRequest.replace(
                                "89a4195d-dc72-478d-85a6-068821f6989c",
                                "b10ec79f-d944-4cf9-818c-164e0d8bd61f")))
                .andExpect(status().isConflict());

        assertThat(countCases(ORGANIZATION_A)).isOne();
    }

    @Test
    void appointmentUniquenessIsScopedToOneOrganization() throws Exception {
        addMembership(ORGANIZATION_B, physician, "CLINICIAN", true);

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(UUID.randomUUID())))
                .andExpect(status().isCreated());
        mockMvc.perform(post(endpoint(ORGANIZATION_B))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(UUID.randomUUID())))
                .andExpect(status().isCreated());

        assertThat(countCases(ORGANIZATION_A)).isOne();
        assertThat(countCases(ORGANIZATION_B)).isOne();
    }

    @Test
    void rejectsPastConsultationDeadline() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "f95dac0f-d7f5-4ecb-a4b9-6689d55d19e6",
                                  "patientReference": "synthetic-patient-42",
                                  "scheduledAt": "2020-01-01T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(countCases(ORGANIZATION_A)).isZero();
    }

    @Test
    void malformedJsonReturnsControlledBadRequest() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());

        assertThat(countCases(ORGANIZATION_A)).isZero();
    }

    @Test
    void creationDoesNotLogPatientOrAppointmentReferences() throws Exception {
        String marker = "synthetic-sensitive-marker-" + UUID.randomUUID();
        var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        rootLogger.addAppender(appender);

        try {
            mockMvc.perform(post(endpoint(ORGANIZATION_A))
                            .header("Authorization", bearerToken(physician))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "requestId": "c1af38aa-ce4d-41f5-9177-da14ec68b64c",
                                      "patientReference": "%s-patient",
                                      "appointmentReference": "%s-appointment"
                                    }
                                    """.formatted(marker, marker)))
                    .andExpect(status().isCreated());
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(marker));
    }

    private String endpoint(String organizationId) {
        return "/api/v1/organizations/" + organizationId + "/preparation-cases";
    }

    private String validRequest() {
        return validRequest(UUID.randomUUID());
    }

    private String validRequest(UUID requestId) {
        return """
                {
                  "requestId": "%s",
                  "patientReference": "synthetic-patient-42",
                  "appointmentReference": "synthetic-appointment-7"
                }
                """.formatted(requestId);
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
                UUID.randomUUID(), organizationId, user.getId(), role, active, Instant.parse("2026-08-21T10:00:00Z"));
    }

    private void addOrganization(String organizationId, String displayName) {
        jdbcTemplate.update(
                "INSERT INTO care_organization (id, display_name) VALUES (?, ?)",
                organizationId,
                displayName);
    }

    private int countCases(String organizationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_case WHERE organization_id = ?",
                Integer.class,
                organizationId);
        return count == null ? 0 : count;
    }

    private int creationStatusAfter(CountDownLatch start, String authorization, String request) throws Exception {
        start.await();
        return mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn().getResponse().getStatus();
    }

    private String bearerToken(AppUtilisateur user) {
        UtilisateurSecurite principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }
}
