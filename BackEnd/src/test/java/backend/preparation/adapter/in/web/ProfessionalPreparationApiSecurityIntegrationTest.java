package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import backend.preparation.adapter.out.storage.LocalPrivateAttachmentStorage;
import backend.preparation.application.port.in.PatientAttachmentUpload;
import backend.preparation.application.port.out.StagedAttachment;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@AutoConfigureMockMvc
class ProfessionalPreparationApiSecurityIntegrationTest {

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
    private LocalPrivateAttachmentStorage attachmentStorage;

    private AppUtilisateur physician;

    @BeforeEach
    void setUp() {
        clearPreparationData();
        physician = createUser("physician-view@synthetic.invalid", Role.MEDECIN);
        addMembership(ORGANIZATION_A, physician, "CLINICIAN", true);
    }

    @AfterEach
    void tearDown() {
        clearPreparationData();
    }

    @Test
    void clinicianGetsABoundedListAndStructuredViewWithoutInternalStorageData() throws Exception {
        String reasonMarker = "synthetic-exact-reason-" + UUID.randomUUID();
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", reasonMarker, NOW.plusSeconds(3600));
        insertAttachment(ORGANIZATION_A, caseId, "synthetic-result.pdf");
        insertCase(ORGANIZATION_B, "CREATED", null, NOW.plusSeconds(7200));

        mockMvc.perform(get(listEndpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.content[0].readiness.state").value("REVIEWABLE"))
                .andExpect(jsonPath("$.content[0].readiness.ruleSetId").value("CORE_PREPARATION"))
                .andExpect(jsonPath("$.content[0].readiness.ruleSetVersion").value(1))
                .andExpect(jsonPath("$.content[0].attachmentPresent").value(true))
                .andExpect(jsonPath("$.content[0].patientReason").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.patientReason.present").value(true))
                .andExpect(jsonPath("$.patientReason.text").value(reasonMarker))
                .andExpect(jsonPath("$.patientReason.provenance").value("PATIENT_DECLARED"))
                .andExpect(jsonPath("$.attachment.present").value(true))
                .andExpect(jsonPath("$.attachment.displayFilename").value("synthetic-result.pdf"))
                .andExpect(jsonPath("$.attachment.mediaType").value("application/pdf"))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.digest").doesNotExist())
                .andExpect(jsonPath("$.attachment.id").doesNotExist());
    }

    @Test
    void optionalAttachmentAbsenceNeverBecomesAReadinessBlocker() throws Exception {
        insertCase(ORGANIZATION_A, "CREATED", null, NOW.plusSeconds(3600));

        mockMvc.perform(get(listEndpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].attachmentPresent").value(false))
                .andExpect(jsonPath("$.content[0].readiness.state").value("INCOMPLETE"))
                .andExpect(jsonPath("$.content[0].readiness.blockers.length()").value(1))
                .andExpect(jsonPath("$.content[0].readiness.blockers[0]")
                        .value("PATIENT_REASON_MISSING"));
    }

    @Test
    void activeMedicalAssistantCanViewButOrganizationAdminCannot() throws Exception {
        insertCase(ORGANIZATION_A, "CREATED", null, NOW.plusSeconds(3600));
        AppUtilisateur assistant = createUser("assistant-view@synthetic.invalid", Role.ASSISTANT_MEDICAL);
        addMembership(ORGANIZATION_A, assistant, "MEDICAL_ASSISTANT", true);
        AppUtilisateur administrator = createUser("admin-view@synthetic.invalid", Role.ADMIN);
        addMembership(ORGANIZATION_A, administrator, "ORGANIZATION_ADMIN", true);

        mockMvc.perform(get(listEndpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(assistant)))
                .andExpect(status().isOk());
        mockMvc.perform(get(listEndpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(administrator)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        jdbcTemplate.update(
                "UPDATE organization_membership SET membership_role = 'ORGANIZATION_ADMIN' WHERE user_id = ?",
                physician.getId());
        mockMvc.perform(get(listEndpoint(ORGANIZATION_A))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantIsolationPreventsCrossOrganizationEnumeration() throws Exception {
        UUID caseA = insertCase(ORGANIZATION_A, "CREATED", null, NOW.plusSeconds(3600));
        insertCase(ORGANIZATION_B, "CREATED", null, NOW.plusSeconds(7200));

        mockMvc.perform(get(listEndpoint(ORGANIZATION_B))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        addMembership(ORGANIZATION_B, physician, "CLINICIAN", true);
        mockMvc.perform(get(viewEndpoint(ORGANIZATION_B, caseA))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void paginationAndAuthenticationFailClosed() throws Exception {
        mockMvc.perform(get(listEndpoint(ORGANIZATION_A) + "?size=101")
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get(listEndpoint(ORGANIZATION_A) + "?page=-1")
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(listEndpoint(ORGANIZATION_A)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void unsupportedPersistedWorkflowEvidenceNeverReturnsPositiveReadiness() throws Exception {
        UUID readyCaseId = insertCase(
                ORGANIZATION_A,
                "READY_FOR_CONSULTATION",
                "Synthetic exact reason",
                NOW.plusSeconds(3600));
        UUID needsActionCaseId = insertCase(
                ORGANIZATION_A,
                "NEEDS_ACTION",
                "Synthetic other exact reason",
                NOW.plusSeconds(7200));

        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, readyCaseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(content().string("Erreur interne du serveur"));
        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, needsActionCaseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Erreur interne du serveur"));
    }

    @Test
    void persistedReadyEvidenceMustMatchTheCurrentCaseRevisionAndReason() throws Exception {
        UUID caseId = insertCase(
                ORGANIZATION_A,
                "READY_FOR_CONSULTATION",
                "Synthetic exact reason",
                NOW.plusSeconds(3600));
        jdbcTemplate.update("UPDATE preparation_case SET version = 1 WHERE id = ?", caseId);
        insertReadyConfirmation(caseId, 0, NOW);

        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness.state").value("READY"));

        jdbcTemplate.update("UPDATE preparation_case SET version = 2 WHERE id = ?", caseId);
        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isInternalServerError());

        jdbcTemplate.update(
                "UPDATE preparation_case SET version = 1, patient_reason_recorded_at = ?, updated_at = ? WHERE id = ?",
                NOW.plusSeconds(1),
                NOW.plusSeconds(1),
                caseId);
        mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void professionalViewDoesNotLogClinicalTextOrAttachmentFilename() throws Exception {
        String marker = "synthetic-professional-view-marker-" + UUID.randomUUID();
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", marker, NOW.plusSeconds(3600));
        insertAttachment(ORGANIZATION_A, caseId, marker + ".pdf");
        var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        rootLogger.addAppender(appender);

        try {
            mockMvc.perform(get(viewEndpoint(ORGANIZATION_A, caseId))
                            .header("Authorization", bearerToken(physician)))
                    .andExpect(status().isOk());
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(marker));
    }

    @Test
    void clinicianDownloadsVerifiedBytesWithDefensiveHeadersOutsideTheDatabaseTransaction() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", "Synthetic exact reason", NOW.plusSeconds(3600));
        byte[] bytes = ("%PDF-1.7\nsynthetic-download-content\n%%EOF")
                .getBytes(StandardCharsets.UTF_8);
        StagedAttachment staged = storeAttachment("résultat synthétique.pdf", bytes);
        insertAttachment(ORGANIZATION_A, caseId, staged);

        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("attachment"),
                                org.hamcrest.Matchers.containsString("filename*="))))
                .andExpect(content().bytes(bytes));

        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(physician))
                        .header("Range", "bytes=0-3"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Range"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void assistantCanDownloadButAdministratorAndWrongTenantFailClosed() throws Exception {
        UUID caseId = insertCase(ORGANIZATION_A, "COLLECTING", "Synthetic exact reason", NOW.plusSeconds(3600));
        byte[] bytes = ("%PDF-1.7\nsynthetic-assistant-content\n%%EOF")
                .getBytes(StandardCharsets.UTF_8);
        insertAttachment(ORGANIZATION_A, caseId, storeAttachment("result.pdf", bytes));
        AppUtilisateur assistant = createUser("assistant-download@synthetic.invalid", Role.ASSISTANT_MEDICAL);
        addMembership(ORGANIZATION_A, assistant, "MEDICAL_ASSISTANT", true);
        AppUtilisateur administrator = createUser("admin-download@synthetic.invalid", Role.ADMIN);
        addMembership(ORGANIZATION_A, administrator, "ORGANIZATION_ADMIN", true);

        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(assistant)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));

        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_A, caseId))
                        .header("Authorization", bearerToken(administrator)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        addMembership(ORGANIZATION_B, physician, "CLINICIAN", true);
        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_B, caseId))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void missingMetadataIs404AndMissingStoredBytesAre503BeforeAnyBodyIsStreamed() throws Exception {
        UUID withoutAttachment = insertCase(
                ORGANIZATION_A, "COLLECTING", "Synthetic exact reason", NOW.plusSeconds(3600));
        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_A, withoutAttachment))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_A, withoutAttachment)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        UUID missingBytes = insertCase(
                ORGANIZATION_A, "COLLECTING", "Synthetic other reason", NOW.plusSeconds(7200));
        insertAttachment(ORGANIZATION_A, missingBytes, "synthetic-missing.pdf");
        mockMvc.perform(get(downloadEndpoint(ORGANIZATION_A, missingBytes))
                        .header("Authorization", bearerToken(physician)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(content().string("Document de préparation temporairement indisponible"));
    }

    private UUID insertCase(String organizationId, String status, String reason, Instant scheduledAt) {
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
                scheduledAt,
                status,
                reason,
                reason == null ? null : "PATIENT_DECLARED",
                reason == null ? null : NOW,
                NOW,
                NOW,
                0L);
        return id;
    }

    private void insertAttachment(String organizationId, UUID caseId, String filename) {
        jdbcTemplate.update(
                """
                INSERT INTO preparation_attachment (
                    id, organization_id, preparation_case_id, storage_key, display_filename,
                    media_type, size_bytes, sha256_digest, provenance, uploaded_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                organizationId,
                caseId,
                UUID.randomUUID(),
                filename,
                "PDF",
                42L,
                "a".repeat(64),
                "PATIENT_UPLOADED",
                NOW,
                NOW,
                0L);
    }

    private void insertAttachment(String organizationId, UUID caseId, StagedAttachment staged) {
        jdbcTemplate.update(
                """
                INSERT INTO preparation_attachment (
                    id, organization_id, preparation_case_id, storage_key, display_filename,
                    media_type, size_bytes, sha256_digest, provenance, uploaded_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                organizationId,
                caseId,
                staged.storageKey().value(),
                staged.displayFilename(),
                staged.mediaType().name(),
                staged.sizeBytes(),
                staged.digest().value(),
                "PATIENT_UPLOADED",
                NOW,
                NOW,
                0L);
    }

    private StagedAttachment storeAttachment(String filename, byte[] bytes) {
        PatientAttachmentUpload upload = new PatientAttachmentUpload() {
            @Override
            public String originalFilename() {
                return filename;
            }

            @Override
            public String declaredContentType() {
                return "application/pdf";
            }

            @Override
            public long declaredSizeBytes() {
                return bytes.length;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
        StagedAttachment staged = attachmentStorage.stage(upload);
        attachmentStorage.promote(staged);
        return staged;
    }

    private void insertReadyConfirmation(UUID caseId, long reviewedRevision, Instant reviewedReasonAt) {
        jdbcTemplate.update(
                """
                INSERT INTO preparation_ready_confirmation (
                    preparation_case_id, organization_id, confirmed_by_user_id, confirmed_at,
                    rule_set_id, rule_set_version, reviewed_case_revision,
                    reviewed_reason_recorded_at, open_task_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                caseId,
                ORGANIZATION_A,
                physician.getId(),
                NOW.plusSeconds(2),
                "CORE_PREPARATION",
                1,
                reviewedRevision,
                reviewedReasonAt,
                0);
    }

    private void clearPreparationData() {
        jdbcTemplate.update("DELETE FROM preparation_audit_event");
        jdbcTemplate.update("DELETE FROM preparation_attachment");
        jdbcTemplate.update("DELETE FROM preparation_ready_confirmation");
        jdbcTemplate.update("DELETE FROM preparation_task");
        jdbcTemplate.update("DELETE FROM patient_access_session");
        jdbcTemplate.update("DELETE FROM patient_invitation");
        jdbcTemplate.update("DELETE FROM preparation_case");
        jdbcTemplate.update("DELETE FROM organization_membership");
        utilisateurRepository.deleteAll();
    }

    private String listEndpoint(String organizationId) {
        return "/api/v1/organizations/" + organizationId + "/preparation-cases";
    }

    private String viewEndpoint(String organizationId, UUID caseId) {
        return listEndpoint(organizationId) + "/" + caseId + "/preparation-view";
    }

    private String downloadEndpoint(String organizationId, UUID caseId) {
        return listEndpoint(organizationId) + "/" + caseId + "/attachment";
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

    private String bearerToken(AppUtilisateur user) {
        UtilisateurSecurite principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }
}
