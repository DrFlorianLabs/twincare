package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.domain.SecureToken;
import backend.security.JwtService;
import backend.security.UtilisateurSecurite;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class PatientInvitationApiSecurityIntegrationTest {

    private static final String ORGANIZATION_A = "cabinet-a";
    private static final String ORGANIZATION_B = "cabinet-b";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppUtilisateurRepository utilisateurRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PatientAccessSessionRepository accessSessionRepository;
    @Autowired
    private SecureTokenDigester secureTokenDigester;

    private AppUtilisateur physician;

    @BeforeEach
    void setUp() {
        clearPreparationData();
        utilisateurRepository.deleteAll();
        physician = createUser("invitation-physician@synthetic.invalid", Role.MEDECIN);
        addMembership(ORGANIZATION_A, physician, "CLINICIAN", true);
    }

    @AfterEach
    void tearDown() {
        clearPreparationData();
        utilisateurRepository.deleteAll();
    }

    @Test
    void staffMemberIssuesARotatingCredentialThatIsNeverStoredInPlaintext() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-issue");

        IssuedInvitation first = issueInvitation(ORGANIZATION_A, caseId, physician);
        IssuedInvitation second = issueInvitation(ORGANIZATION_A, caseId, physician);

        assertThat(second.token()).isNotEqualTo(first.token()).hasSize(43);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_invitation WHERE preparation_case_id = ? AND status = 'ACTIVE'",
                Integer.class,
                caseId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_invitation WHERE preparation_case_id = ? AND status = 'REVOKED'",
                Integer.class,
                caseId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT token_digest FROM patient_invitation WHERE id = ?",
                String.class,
                second.id()))
                .hasSize(64)
                .isNotEqualTo(second.token());
        assertThat(countStoredValue(first.token())).isZero();
        assertThat(countStoredValue(second.token())).isZero();
    }

    @Test
    void invitationIssueRequiresStaffMembershipInTheSelectedOrganization() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-authz");
        AppUtilisateur patient = createUser("invitation-patient@synthetic.invalid", Role.PATIENT);

        mockMvc.perform(post(invitationCollection(ORGANIZATION_A, caseId)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(invitationCollection(ORGANIZATION_A, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(patient)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(invitationCollection(ORGANIZATION_B, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician)))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM patient_invitation", Integer.class)).isZero();
    }

    @Test
    void caseIdentifierFromAnotherOrganizationIsIndistinguishableFromMissing() throws Exception {
        UUID caseInOrganizationA = createCase(ORGANIZATION_A, physician, "appointment-cross-org");
        addMembership(ORGANIZATION_B, physician, "CLINICIAN", true);

        mockMvc.perform(post(invitationCollection(ORGANIZATION_B, caseInOrganizationA))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician)))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .isEqualTo("Préparation introuvable"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM patient_invitation", Integer.class)).isZero();
    }

    @Test
    void anonymousExchangeConsumesInvitationOnceAndCreatesAScopedHttpOnlySession() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-exchange");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);

        MvcResult exchanged = exchange(invitation.token())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("tc_preparation_session="),
                        org.hamcrest.Matchers.containsString("Path=/api/v1/patient-access"),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"))))
                .andExpect(jsonPath("$.status").value("PATIENT_STARTED"))
                .andExpect(jsonPath("$.preparationCaseId").doesNotExist())
                .andReturn();

        String sessionToken = cookieValue(exchanged.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM preparation_case WHERE id = ?",
                String.class,
                caseId)).isEqualTo("PATIENT_STARTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM patient_invitation WHERE id = ?",
                String.class,
                invitation.id())).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_access_session WHERE invitation_id = ?",
                Integer.class,
                invitation.id())).isOne();
        assertThat(countStoredValue(invitation.token())).isZero();
        assertThat(countStoredValue(sessionToken)).isZero();

        exchange(invitation.token())
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .isEqualTo("Invitation patient indisponible"));
    }

    @Test
    void patientSessionResolvesToOneOpaqueActiveContextAndIssuesBoundCsrf() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-session-resume");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);
        String sessionToken = exchangedSessionToken(invitation.token());

        assertThat(accessSessionRepository.findScopeByTokenDigest(
                secureTokenDigester.digestSession(new SecureToken(sessionToken))))
                .isPresent();

        MvcResult resumed = mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie(sessionToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string(PatientCsrfTokenRepository.HEADER_NAME,
                        org.hamcrest.Matchers.matchesPattern("[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.preparationCaseId").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andReturn();

        assertThat(resumed.getResponse().getContentAsString())
                .doesNotContain(caseId.toString(), ORGANIZATION_A, sessionToken);
    }

    @Test
    void absentMultipleMalformedUnknownExpiredRevokedAndTerminalSessionsShareOneFailure() throws Exception {
        UUID expiredCase = createCase(ORGANIZATION_A, physician, "appointment-session-expired");
        IssuedInvitation expiredInvitation = issueInvitation(ORGANIZATION_A, expiredCase, physician);
        String expiredSession = exchangedSessionToken(expiredInvitation.token());
        jdbcTemplate.update(
                "UPDATE patient_access_session SET expires_at = ? WHERE preparation_case_id = ?",
                Instant.now().minusSeconds(1), expiredCase);

        UUID revokedCase = createCase(ORGANIZATION_A, physician, "appointment-session-revoked");
        IssuedInvitation revokedInvitation = issueInvitation(ORGANIZATION_A, revokedCase, physician);
        String revokedSession = exchangedSessionToken(revokedInvitation.token());
        revokeInvitation(ORGANIZATION_A, revokedCase, revokedInvitation.id(), physician)
                .andExpect(status().isNoContent());

        UUID terminalCase = createCase(ORGANIZATION_A, physician, "appointment-session-terminal");
        IssuedInvitation terminalInvitation = issueInvitation(ORGANIZATION_A, terminalCase, physician);
        String terminalSession = exchangedSessionToken(terminalInvitation.token());
        jdbcTemplate.update(
                "UPDATE preparation_case SET status = 'CANCELLED', updated_at = ? WHERE id = ?",
                Instant.now(), terminalCase);

        List<MvcResult> failures = List.of(
                mockMvc.perform(get("/api/v1/patient-access/session")).andReturn(),
                mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie("malformed"))).andReturn(),
                mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie("Z".repeat(43)))).andReturn(),
                mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie(expiredSession))).andReturn(),
                mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie(revokedSession))).andReturn(),
                mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie(terminalSession))).andReturn(),
                mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie(terminalSession), sessionCookie("Y".repeat(43)))).andReturn());

        assertThat(failures).allSatisfy(result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getContentAsString()).isEqualTo("Session patient indisponible");
            assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                    .contains("tc_preparation_session=", "Max-Age=0", "SameSite=Strict");
        });
    }

    @Test
    void jwtRolesCannotAuthenticatePatientSessionRoutes() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-session-jwt");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);
        String sessionToken = exchangedSessionToken(invitation.token());

        mockMvc.perform(get("/api/v1/patient-access/session")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patient-access/session")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(physician))
                        .cookie(sessionCookie("Z".repeat(43))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patient-access/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed")
                        .cookie(sessionCookie(sessionToken)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRequiresSessionBoundCsrfAndDedicatedPatientOrigin() throws Exception {
        UUID firstCase = createCase(ORGANIZATION_A, physician, "appointment-session-logout-a");
        IssuedInvitation firstInvitation = issueInvitation(ORGANIZATION_A, firstCase, physician);
        String firstSession = exchangedSessionToken(firstInvitation.token());
        String firstCsrf = csrfFor(firstSession);

        UUID secondCase = createCase(ORGANIZATION_A, physician, "appointment-session-logout-b");
        IssuedInvitation secondInvitation = issueInvitation(ORGANIZATION_A, secondCase, physician);
        String secondSession = exchangedSessionToken(secondInvitation.token());
        String secondCsrf = csrfFor(secondSession);
        assertThat(secondCsrf).isNotEqualTo(firstCsrf);

        patientLogout(firstSession, null, "http://localhost:3000")
                .andExpect(status().isForbidden());
        patientLogout(firstSession, "Z".repeat(43), "http://localhost:3000")
                .andExpect(status().isForbidden());
        patientLogout(firstSession, secondCsrf, "http://localhost:3000")
                .andExpect(status().isForbidden());
        patientLogout(firstSession, firstCsrf, null)
                .andExpect(status().isForbidden());
        patientLogout(firstSession, firstCsrf, "http://localhost:5173")
                .andExpect(status().isForbidden());

        patientLogout(firstSession, firstCsrf, "http://localhost:3000")
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("tc_preparation_session="),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"))));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM patient_access_session WHERE preparation_case_id = ?",
                Boolean.class,
                firstCase)).isTrue();
        mockMvc.perform(get("/api/v1/patient-access/session").cookie(sessionCookie(firstSession)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPatientRoutesRemainFailClosed() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-session-unknown-route");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);
        String sessionToken = exchangedSessionToken(invitation.token());

        mockMvc.perform(get("/api/v1/patient-access/unknown").cookie(sessionCookie(sessionToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patient-access/unknown")
                        .cookie(sessionCookie(sessionToken))
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(PatientCsrfTokenRepository.HEADER_NAME, csrfFor(sessionToken)))
                .andExpect(status().isForbidden());
    }

    @RepeatedTest(3)
    void concurrentPatientLogoutAndProfessionalRevocationRemainIdempotent() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-session-concurrent-logout");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);
        String sessionToken = exchangedSessionToken(invitation.token());
        String csrfToken = csrfFor(sessionToken);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var patientLogout = executor.submit(() -> {
                start.await();
                return patientLogout(sessionToken, csrfToken, "http://localhost:3000")
                        .andReturn().getResponse().getStatus();
            });
            var professionalRevoke = executor.submit(() -> {
                start.await();
                return revokeInvitation(ORGANIZATION_A, caseId, invitation.id(), physician)
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertThat(List.of(patientLogout.get(), professionalRevoke.get()))
                    .containsOnly(204);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM patient_access_session WHERE preparation_case_id = ?",
                Boolean.class,
                caseId)).isTrue();
    }

    @Test
    void expiredRevokedConsumedAndRandomTokensHaveTheSamePublicFailure() throws Exception {
        UUID expiredCase = createCase(ORGANIZATION_A, physician, "appointment-expired");
        IssuedInvitation expired = issueInvitation(ORGANIZATION_A, expiredCase, physician);
        jdbcTemplate.update(
                """
                UPDATE patient_invitation
                SET issued_at = ?, expires_at = ?, updated_at = ?
                WHERE id = ?
                """,
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(3600),
                expired.id());

        UUID revokedCase = createCase(ORGANIZATION_A, physician, "appointment-revoked");
        IssuedInvitation revoked = issueInvitation(ORGANIZATION_A, revokedCase, physician);
        revokeInvitation(ORGANIZATION_A, revokedCase, revoked.id(), physician)
                .andExpect(status().isNoContent());

        UUID cancelledCase = createCase(ORGANIZATION_A, physician, "appointment-cancelled");
        IssuedInvitation cancelled = issueInvitation(ORGANIZATION_A, cancelledCase, physician);
        jdbcTemplate.update(
                "UPDATE preparation_case SET status = 'CANCELLED', updated_at = ? WHERE id = ?",
                Instant.now(),
                cancelledCase);

        List<String> bodies = new ArrayList<>();
        for (String token : List.of(expired.token(), revoked.token(), cancelled.token(), "Z".repeat(43))) {
            MvcResult result = exchange(token).andExpect(status().isUnauthorized()).andReturn();
            bodies.add(result.getResponse().getContentAsString());
        }

        assertThat(bodies).containsOnly("Invitation patient indisponible");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_access_session WHERE preparation_case_id = ?",
                Integer.class,
                cancelledCase)).isZero();
    }

    @Test
    void professionalRevocationInvalidatesTheSessionCreatedFromTheInvitation() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-revoke-session");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);
        exchange(invitation.token()).andExpect(status().isOk());

        revokeInvitation(ORGANIZATION_A, caseId, invitation.id(), physician)
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM patient_invitation WHERE id = ?",
                String.class,
                invitation.id())).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM patient_access_session WHERE invitation_id = ?",
                Boolean.class,
                invitation.id())).isTrue();
    }

    @Test
    void secretInQueryOrPathIsNeverAcceptedAsPatientAuthentication() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-url-secret");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);

        mockMvc.perform(post("/api/v1/patient-access/exchange/" + invitation.token()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patient-access/exchange")
                        .queryParam("token", invitation.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM patient_invitation WHERE id = ?",
                String.class,
                invitation.id())).isEqualTo("ACTIVE");
    }

    @RepeatedTest(5)
    void concurrentExchangeProducesExactlyOneSession() throws Exception {
        UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-concurrent");
        IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> exchangeStatusAfter(start, invitation.token()));
            var second = executor.submit(() -> exchangeStatusAfter(start, invitation.token()));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_access_session WHERE invitation_id = ?",
                Integer.class,
                invitation.id())).isOne();
    }

    @Test
    void invitationAndSessionSecretsNeverAppearInApplicationLogs() throws Exception {
        var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        rootLogger.addAppender(appender);
        String invitationToken;
        String sessionToken;
        String csrfToken;

        try {
            UUID caseId = createCase(ORGANIZATION_A, physician, "appointment-log-safety");
            IssuedInvitation invitation = issueInvitation(ORGANIZATION_A, caseId, physician);
            invitationToken = invitation.token();
            MvcResult exchange = exchange(invitationToken).andExpect(status().isOk()).andReturn();
            sessionToken = cookieValue(exchange.getResponse().getHeader(HttpHeaders.SET_COOKIE));
            csrfToken = csrfFor(sessionToken);
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(invitationToken)
                        || message.contains(sessionToken)
                        || message.contains(csrfToken));
    }

    private UUID createCase(String organizationId, AppUtilisateur user, String appointmentReference) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/" + organizationId + "/preparation-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientReference": "synthetic-patient",
                                  "appointmentReference": "%s"
                                }
                                """.formatted(appointmentReference)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("id").toString());
    }

    private IssuedInvitation issueInvitation(String organizationId, UUID caseId, AppUtilisateur user) throws Exception {
        MvcResult result = mockMvc.perform(post(invitationCollection(organizationId, caseId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        Map<String, Object> body = json(result);
        return new IssuedInvitation(
                UUID.fromString(body.get("invitationId").toString()),
                body.get("invitationToken").toString());
    }

    private org.springframework.test.web.servlet.ResultActions revokeInvitation(
            String organizationId,
            UUID caseId,
            UUID invitationId,
            AppUtilisateur user) throws Exception {
        return mockMvc.perform(delete(invitationCollection(organizationId, caseId) + "/" + invitationId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user)));
    }

    private org.springframework.test.web.servlet.ResultActions exchange(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/patient-access/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private String exchangedSessionToken(String invitationToken) throws Exception {
        MvcResult result = exchange(invitationToken).andExpect(status().isOk()).andReturn();
        return cookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private String csrfFor(String sessionToken) throws Exception {
        return mockMvc.perform(get("/api/v1/patient-access/session")
                        .cookie(sessionCookie(sessionToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(PatientCsrfTokenRepository.HEADER_NAME);
    }

    private org.springframework.test.web.servlet.ResultActions patientLogout(
            String sessionToken,
            String csrfToken,
            String origin) throws Exception {
        var request = delete("/api/v1/patient-access/session").cookie(sessionCookie(sessionToken));
        if (csrfToken != null) {
            request.header(PatientCsrfTokenRepository.HEADER_NAME, csrfToken);
        }
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        return mockMvc.perform(request);
    }

    private Cookie sessionCookie(String value) {
        return new Cookie(PatientAccessController.SESSION_COOKIE, value);
    }

    private int exchangeStatusAfter(CountDownLatch start, String token) throws Exception {
        start.await();
        return exchange(token).andReturn().getResponse().getStatus();
    }

    private String invitationCollection(String organizationId, UUID caseId) {
        return "/api/v1/organizations/" + organizationId + "/preparation-cases/" + caseId + "/invitations";
    }

    private Map<String, Object> json(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<>() {
        });
    }

    private String cookieValue(String setCookie) {
        assertThat(setCookie).isNotNull();
        String prefix = PatientAccessController.SESSION_COOKIE + "=";
        return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
    }

    private int countStoredValue(String rawSecret) {
        Integer invitationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_invitation WHERE token_digest = ?",
                Integer.class,
                rawSecret);
        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_access_session WHERE token_digest = ?",
                Integer.class,
                rawSecret);
        return (invitationCount == null ? 0 : invitationCount) + (sessionCount == null ? 0 : sessionCount);
    }

    private void clearPreparationData() {
        jdbcTemplate.update("DELETE FROM patient_access_session");
        jdbcTemplate.update("DELETE FROM patient_invitation");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
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
                UUID.randomUUID(), organizationId, user.getId(), role, active, Instant.now());
    }

    private String bearerToken(AppUtilisateur user) {
        UtilisateurSecurite principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }

    private record IssuedInvitation(UUID id, String token) {
    }
}
