package backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import backend.dao.AppUtilisateurRepository;
import backend.dao.DocumentRepository;
import backend.dao.DossierPatientRepository;
import backend.dao.InteractionChatbotRepository;
import backend.dao.ValidationRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Document;
import backend.entity.DossierPatient;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.entity.StatutDocument;
import backend.entity.TypeDocument;
import backend.service.FileStorageService;
import backend.service.AuthService;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String UPLOAD_DIRECTORY = System.getProperty("java.io.tmpdir")
            + "/twincare-security-tests-" + UUID.randomUUID();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> UPLOAD_DIRECTORY);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppUtilisateurRepository utilisateurRepository;
    @Autowired
    private DossierPatientRepository dossierRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private InteractionChatbotRepository interactionRepository;
    @Autowired
    private ValidationRepository validationRepository;

    private final List<String> storedFiles = new ArrayList<>();

    private AppUtilisateur patientA;
    private AppUtilisateur patientB;
    private DossierPatient dossierA;
    private DossierPatient dossierB;
    private Document documentA;
    private Document documentB;

    @BeforeEach
    void setUp() {
        validationRepository.deleteAll();
        interactionRepository.deleteAll();
        documentRepository.deleteAll();
        dossierRepository.deleteAll();
        utilisateurRepository.deleteAll();

        patientA = createUser("patient-a@synthetic.invalid", Role.PATIENT);
        patientB = createUser("patient-b@synthetic.invalid", Role.PATIENT);
        dossierA = createDossier(patientA);
        dossierB = createDossier(patientB);
        documentA = createDocument(dossierA, "synthetic-a.pdf");
        documentB = createDocument(dossierB, "synthetic-b.pdf");
    }

    @AfterEach
    void tearDown() {
        validationRepository.deleteAll();
        interactionRepository.deleteAll();
        documentRepository.deleteAll();
        dossierRepository.deleteAll();
        utilisateurRepository.deleteAll();
        storedFiles.forEach(fileStorageService::deleteFile);
        storedFiles.clear();
    }

    @Test
    void anonymousDocumentContentAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/documents/{id}/content", documentA.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotReadAnotherPatientsDocument() throws Exception {
        mockMvc.perform(get("/api/documents/{id}/content", documentB.getId())
                        .header("Authorization", bearerToken(patientA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCanReadOwnDocumentWithoutCachingOrMimeSniffing() throws Exception {
        mockMvc.perform(get("/api/documents/{id}/content", documentA.getId())
                        .header("Authorization", bearerToken(patientA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void patientCannotEnumerateDossiersOrUsers() throws Exception {
        String authorization = bearerToken(patientA);

        mockMvc.perform(get("/api/dossiers").header("Authorization", authorization))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header("Authorization", authorization))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotDeleteAnotherAccount() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", patientB.getId())
                        .header("Authorization", bearerToken(patientA)))
                .andExpect(status().isForbidden());

        assertThat(utilisateurRepository.existsById(patientB.getId())).isTrue();
        assertThat(Files.exists(Path.of(UPLOAD_DIRECTORY, documentB.getCheminFichier()))).isTrue();
    }

    @Test
    void patientCannotProvisionAccountsOrResetPasswords() throws Exception {
        String authorization = bearerToken(patientA);

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/reset-password")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotAccessApiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", bearerToken(patientA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorDeletionRemovesPatientMetadataAndStoredDocuments() throws Exception {
        AppUtilisateur administrator = createUser("admin@synthetic.invalid", Role.ADMIN);
        String storageKey = documentB.getCheminFichier();

        mockMvc.perform(delete("/api/users/{id}", patientB.getId())
                        .header("Authorization", bearerToken(administrator)))
                .andExpect(status().isOk());

        assertThat(utilisateurRepository.existsById(patientB.getId())).isFalse();
        assertThat(documentRepository.existsById(documentB.getId())).isFalse();
        assertThat(Files.exists(Path.of(UPLOAD_DIRECTORY, storageKey))).isFalse();
    }

    @Test
    void malformedBearerTokenIsRejectedWithoutServerError() throws Exception {
        mockMvc.perform(get("/api/dossiers/{id}", dossierA.getId())
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void successfulAuthenticationDoesNotLogTheUserEmail() throws Exception {
        String marker = "synthetic-phi-marker-" + UUID.randomUUID();
        String email = marker + "@synthetic.invalid";
        createUser(email, Role.PATIENT);

        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","motDePasse":"synthetic-password"}
                                    """.formatted(email)))
                    .andExpect(status().isOk());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(marker));
    }

    private AppUtilisateur createUser(String email, Role role) {
        return utilisateurRepository.save(new AppUtilisateur(
                "Synthetic",
                "Patient",
                LocalDate.of(1990, 1, 1),
                email,
                passwordEncoder.encode("synthetic-password"),
                role,
                Sexe.NON_PRECISE));
    }

    private DossierPatient createDossier(AppUtilisateur patient) {
        DossierPatient dossier = new DossierPatient();
        dossier.setPatient(patient);
        DossierPatient saved = dossierRepository.save(dossier);
        patient.setDossierPatient(saved);
        return saved;
    }

    private Document createDocument(DossierPatient dossier, String displayName) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                displayName,
                "application/pdf",
                "%PDF-1.7\nsynthetic integration fixture".getBytes());
        String storageKey = fileStorageService.storeFile(file);
        storedFiles.add(storageKey);

        Document document = new Document();
        document.setType(TypeDocument.AUTRE);
        document.setDateDocument(LocalDate.of(2026, 1, 1));
        document.setStatut(StatutDocument.EN_ATTENTE);
        document.setNomFichier(displayName);
        document.setCheminFichier(storageKey);
        document.setDossierPatient(dossier);
        return documentRepository.save(document);
    }

    private String bearerToken(AppUtilisateur user) {
        UtilisateurSecurite principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }
}
