package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
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
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        utilisateurRepository.deleteAll();
        physician = createUser("physician@synthetic.invalid", Role.MEDECIN);
        addMembership(ORGANIZATION_A, physician, "CLINICIAN", true);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        utilisateurRepository.deleteAll();
    }

    @Test
    void organizationMemberCanCreateAMinimalCase() throws Exception {
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientReference": "synthetic-patient-42",
                                  "appointmentReference": "synthetic-appointment-7"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/organizations/cabinet-a/preparation-cases/")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
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
                                {"patientReference":"synthetic-patient-42"}
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
    void duplicateAppointmentReturnsControlledConflict() throws Exception {
        String request = validRequest();
        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());

        assertThat(countCases(ORGANIZATION_A)).isOne();
    }

    @Test
    void duplicatePatientAndDeadlineWithoutAppointmentReturnsConflict() throws Exception {
        String deadlineOnlyRequest = """
                {
                  "patientReference": "synthetic-patient-42",
                  "scheduledAt": "2026-08-22T10:00:00Z"
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
                        .content(deadlineOnlyRequest))
                .andExpect(status().isConflict());

        assertThat(countCases(ORGANIZATION_A)).isOne();
    }

    @Test
    void appointmentUniquenessIsScopedToOneOrganization() throws Exception {
        addMembership(ORGANIZATION_B, physician, "CLINICIAN", true);

        mockMvc.perform(post(endpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(post(endpoint(ORGANIZATION_B))
                        .header("Authorization", bearerToken(physician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
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
        return """
                {
                  "patientReference": "synthetic-patient-42",
                  "appointmentReference": "synthetic-appointment-7"
                }
                """;
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

    private int countCases(String organizationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_case WHERE organization_id = ?",
                Integer.class,
                organizationId);
        return count == null ? 0 : count;
    }

    private String bearerToken(AppUtilisateur user) {
        UtilisateurSecurite principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }
}
