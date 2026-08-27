package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.domain.SecureToken;
import backend.security.JwtService;
import backend.security.UtilisateurSecurite;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PreparationAuditRollbackIntegrationTest.FailingAuditConfiguration.class)
class PreparationAuditRollbackIntegrationTest {

    private static final String ORGANIZATION_ID = "audit-rollback-org";
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "twincare-audit-rollback-" + UUID.randomUUID());

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppUtilisateurRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SecureTokenDigester tokenDigester;

    private AppUtilisateur physician;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.preparation.attachment-storage-dir", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        clearData();
        jdbcTemplate.update(
                "INSERT INTO care_organization (id, display_name) VALUES (?, ?)",
                ORGANIZATION_ID, "Cabinet rollback");
        physician = users.save(new AppUtilisateur(
                "Synthetic", "Rollback", LocalDate.of(1990, 1, 1),
                "audit-rollback@synthetic.invalid", passwordEncoder.encode("synthetic-password"),
                Role.MEDECIN, Sexe.NON_PRECISE));
        jdbcTemplate.update(
                """
                INSERT INTO organization_membership (
                    id, organization_id, user_id, membership_role, active, created_at
                ) VALUES (?, ?, ?, 'CLINICIAN', TRUE, ?)
                """,
                UUID.randomUUID(), ORGANIZATION_ID, physician.getId(), NOW);
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void businessMutationRollsBackWhenItsAuditEvidenceCannotBeStored() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/" + ORGANIZATION_ID + "/preparation-cases")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId":"234c7cb0-d200-4c03-92fe-a99817676ec8",
                                  "patientReference":"synthetic-rollback",
                                  "appointmentReference":"rollback-appointment"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preparation_case", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preparation_audit_event", Integer.class)).isZero();
    }

    @Test
    void auditFailureOverridesTheNoRollbackExpiryExceptionAndRestoresTheInvitation() throws Exception {
        UUID caseId = insertCase("CREATED", "expiry-rollback");
        SecureToken invitationToken = new SecureToken("E".repeat(SecureToken.ENCODED_LENGTH));
        UUID invitationId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(7_200);
        var digest = tokenDigester.digestInvitation(invitationToken);
        jdbcTemplate.update(
                """
                INSERT INTO patient_invitation (
                    id, organization_id, preparation_case_id, purpose,
                    token_key_id, token_digest, status, active_slot,
                    issued_by_user_id, issued_at, expires_at, updated_at, version
                ) VALUES (?, ?, ?, 'PREPARATION_CASE_ACCESS', ?, ?, 'ACTIVE', 'ACTIVE', ?, ?, ?, ?, 0)
                """,
                invitationId, ORGANIZATION_ID, caseId,
                digest.keyId(), digest.value(), physician.getId(),
                issuedAt, issuedAt.plusSeconds(3_600), issuedAt);

        mockMvc.perform(post("/api/v1/patient-access/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(invitationToken.value())))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM patient_invitation WHERE id = ?",
                String.class,
                invitationId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_access_session WHERE invitation_id = ?",
                Integer.class,
                invitationId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isZero();
    }

    @Test
    void attachmentMetadataAndPromotedBytesRollBackWhenAuditFails() throws Exception {
        UUID caseId = insertCase("PATIENT_STARTED", "attachment-rollback");
        UUID invitationId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(600);
        SecureToken invitationToken = new SecureToken("I".repeat(SecureToken.ENCODED_LENGTH));
        var invitationDigest = tokenDigester.digestInvitation(invitationToken);
        jdbcTemplate.update(
                """
                INSERT INTO patient_invitation (
                    id, organization_id, preparation_case_id, purpose,
                    token_key_id, token_digest, status, active_slot,
                    issued_by_user_id, issued_at, expires_at, consumed_at, updated_at, version
                ) VALUES (?, ?, ?, 'PREPARATION_CASE_ACCESS', ?, ?, 'CONSUMED', NULL, ?, ?, ?, ?, ?, 0)
                """,
                invitationId, ORGANIZATION_ID, caseId,
                invitationDigest.keyId(), invitationDigest.value(), physician.getId(),
                issuedAt, issuedAt.plusSeconds(7_200), issuedAt.plusSeconds(30), issuedAt.plusSeconds(30));

        UUID sessionId = UUID.randomUUID();
        SecureToken sessionToken = new SecureToken("S".repeat(SecureToken.ENCODED_LENGTH));
        var sessionDigest = tokenDigester.digestSession(sessionToken);
        jdbcTemplate.update(
                """
                INSERT INTO patient_access_session (
                    id, invitation_id, organization_id, preparation_case_id, scope,
                    token_key_id, token_digest, issued_at, expires_at, version
                ) VALUES (?, ?, ?, ?, 'PREPARATION_CASE_CONTRIBUTE', ?, ?, ?, ?, 0)
                """,
                sessionId, invitationId, ORGANIZATION_ID, caseId,
                sessionDigest.keyId(), sessionDigest.value(),
                issuedAt.plusSeconds(30), issuedAt.plusSeconds(7_200));

        Cookie cookie = new Cookie(PatientAccessController.SESSION_COOKIE, sessionToken.value());
        String csrf = mockMvc.perform(get("/api/v1/patient-access/session").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(PatientCsrfTokenRepository.HEADER_NAME);
        byte[] pdf = "%PDF-1.7\nSYNTHETIC-AUDIT-ROLLBACK\n%%EOF"
                .getBytes(StandardCharsets.US_ASCII);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/patient-access/attachment")
                        .file(new MockMultipartFile("file", "synthetic.pdf", MediaType.APPLICATION_PDF_VALUE, pdf))
                        .cookie(cookie)
                        .header(PatientCsrfTokenRepository.HEADER_NAME, csrf)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_attachment WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isZero();
        assertThat(countRegularStorageFiles()).isZero();
    }

    @Test
    void closureAndCredentialRevocationRollBackWhenAuditFails() throws Exception {
        UUID caseId = UUID.randomUUID();
        Instant reasonAt = NOW;
        Instant confirmedAt = NOW.plusSeconds(60);
        jdbcTemplate.update(
                """
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference,
                    status, patient_reason, patient_reason_provenance,
                    patient_reason_recorded_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'READY_FOR_CONSULTATION', ?,
                    'PATIENT_DECLARED', ?, ?, ?, 1)
                """,
                caseId,
                ORGANIZATION_ID,
                "synthetic-closure-patient",
                "synthetic-closure-appointment",
                "Synthetic reason",
                reasonAt,
                NOW.minusSeconds(60),
                confirmedAt);
        jdbcTemplate.update(
                """
                INSERT INTO preparation_ready_confirmation (
                    preparation_case_id, organization_id, confirmed_by_user_id, confirmed_at,
                    rule_set_id, rule_set_version, reviewed_case_revision,
                    reviewed_reason_recorded_at, open_task_count
                ) VALUES (?, ?, ?, ?, 'CORE_PREPARATION', 1, 0, ?, 0)
                """,
                caseId,
                ORGANIZATION_ID,
                physician.getId(),
                confirmedAt,
                reasonAt);
        UUID invitationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO patient_invitation (
                    id, organization_id, preparation_case_id, purpose,
                    token_key_id, token_digest, status, active_slot,
                    issued_by_user_id, issued_at, expires_at, consumed_at, updated_at, version
                ) VALUES (?, ?, ?, 'PREPARATION_CASE_ACCESS', 'test-key', ?,
                    'CONSUMED', NULL, ?, ?, ?, ?, ?, 0)
                """,
                invitationId,
                ORGANIZATION_ID,
                caseId,
                "c".repeat(64),
                physician.getId(),
                NOW.minusSeconds(300),
                NOW.plusSeconds(604800),
                NOW.minusSeconds(120),
                NOW.minusSeconds(120));
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO patient_access_session (
                    id, invitation_id, organization_id, preparation_case_id, scope,
                    token_key_id, token_digest, issued_at, expires_at, version
                ) VALUES (?, ?, ?, ?, 'PREPARATION_CASE_CONTRIBUTE',
                    'test-key', ?, ?, ?, 0)
                """,
                sessionId,
                invitationId,
                ORGANIZATION_ID,
                caseId,
                "d".repeat(64),
                NOW.minusSeconds(120),
                NOW.plusSeconds(604800));

        mockMvc.perform(put("/api/v1/organizations/" + ORGANIZATION_ID
                        + "/preparation-cases/" + caseId + "/closure")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM preparation_case WHERE id = ?",
                String.class,
                caseId)).isEqualTo("READY_FOR_CONSULTATION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_case_closure WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM patient_invitation WHERE id = ?",
                String.class,
                invitationId)).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NULL FROM patient_access_session WHERE id = ?",
                Boolean.class,
                sessionId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preparation_audit_event WHERE preparation_case_id = ?",
                Integer.class,
                caseId)).isZero();
    }

    private UUID insertCase(String status, String appointmentReference) {
        UUID caseId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(900);
        jdbcTemplate.update(
                """
                INSERT INTO preparation_case (
                    id, organization_id, patient_reference, appointment_reference,
                    status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """,
                caseId, ORGANIZATION_ID, "synthetic-patient", appointmentReference,
                status, createdAt, createdAt);
        return caseId;
    }

    private long countRegularStorageFiles() throws Exception {
        if (!Files.exists(STORAGE_ROOT)) {
            return 0;
        }
        try (var files = Files.walk(STORAGE_ROOT)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private String bearerToken() {
        var principal = new UtilisateurSecurite(
                physician.getEmail(), physician.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(physician.getRole().name())));
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

    @AfterAll
    static void removeStorageRoot() throws Exception {
        if (!Files.exists(STORAGE_ROOT)) {
            return;
        }
        try (var paths = Files.walk(STORAGE_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration {

        @Bean
        @Primary
        PreparationAuditEventStore failingPreparationAuditEventStore() {
            return event -> {
                throw new IllegalStateException("Synthetic audit persistence failure");
            };
        }
    }
}
