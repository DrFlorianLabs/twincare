package backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Role;
import backend.entity.Sexe;
import backend.rest.ChatbotController;
import backend.rest.DocumentController;
import backend.rest.DossierPatientController;
import backend.rest.InteractionChatbotController;
import backend.rest.LegacyAccountAdministrationController;
import backend.rest.UtilisateurController;
import backend.rest.ValidationController;
import backend.service.AuthService;
import backend.service.FileStorageRecoveryService;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String PASSWORD = "synthetic-password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppUtilisateurRepository utilisateurRepository;
    @Autowired
    private RequestMappingHandlerMapping requestMappings;
    @Autowired
    private ApplicationContext applicationContext;

    private final Map<Role, AppUtilisateur> users = new EnumMap<>(Role.class);

    @BeforeEach
    void setUp() {
        utilisateurRepository.deleteAll();
        for (Role role : Role.values()) {
            users.put(role, createUser(role));
        }
    }

    @AfterEach
    void tearDown() {
        utilisateurRepository.deleteAll();
        users.clear();
    }

    @Test
    void legacyPrototypeControllersAndStartupRecoveryAreAbsentFromActiveRuntime() {
        var mappedControllerTypes = requestMappings.getHandlerMethods().values().stream()
                .map(handler -> handler.getBeanType().getName())
                .toList();

        assertThat(mappedControllerTypes).doesNotContain(
                UtilisateurController.class.getName(),
                DossierPatientController.class.getName(),
                DocumentController.class.getName(),
                ValidationController.class.getName(),
                InteractionChatbotController.class.getName(),
                ChatbotController.class.getName(),
                LegacyAccountAdministrationController.class.getName());
        assertThat(applicationContext.getBeansOfType(FileStorageRecoveryService.class)).isEmpty();
    }

    @Test
    void anonymousRequestsToLegacyAndUnknownApisFailClosed() throws Exception {
        for (MockHttpServletRequestBuilder request : legacyAndUnknownRequests()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyAuthenticatedRoleIsForbiddenFromLegacyAndUnknownApis(Role role) throws Exception {
        for (MockHttpServletRequestBuilder request : legacyAndUnknownRequests()) {
            mockMvc.perform(request.header("Authorization", bearerToken(users.get(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void administratorCannotAccessApiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", bearerToken(users.get(Role.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoSurfaceIsClosedOutsideTheDemoProfile() throws Exception {
        mockMvc.perform(get("/demo/api/state"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBearerTokenIsRejectedWithoutServerError() throws Exception {
        mockMvc.perform(get("/api/v1/professional-context")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void professionalLoginRemainsAvailableAndIsNotCacheable() throws Exception {
        AppUtilisateur physician = users.get(Role.MEDECIN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(physician.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void successfulAuthenticationDoesNotLogTheUserEmail() throws Exception {
        String marker = "synthetic-phi-marker-" + UUID.randomUUID();
        AppUtilisateur user = utilisateurRepository.save(new AppUtilisateur(
                "Synthetic",
                "Professional",
                LocalDate.of(1990, 1, 1),
                marker + "@synthetic.invalid",
                passwordEncoder.encode(PASSWORD),
                Role.MEDECIN,
                Sexe.NON_PRECISE));

        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","motDePasse":"%s"}
                                    """.formatted(user.getEmail(), PASSWORD)))
                    .andExpect(status().isOk());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(marker));
    }

    private List<MockHttpServletRequestBuilder> legacyAndUnknownRequests() {
        return List.of(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/users"),
                get("/api/dossiers"),
                get("/api/documents/1"),
                post("/api/validations/document/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/interactions/dossier/1"),
                get("/api/chatbot/session/patient/1"),
                get("/api/environnement"),
                get("/api/parcours"),
                get("/api/v1/unknown"),
                get("/api/unknown"));
    }

    private AppUtilisateur createUser(Role role) {
        return utilisateurRepository.save(new AppUtilisateur(
                "Synthetic",
                role.name(),
                LocalDate.of(1990, 1, 1),
                role.name().toLowerCase() + "@synthetic.invalid",
                passwordEncoder.encode(PASSWORD),
                role,
                Sexe.NON_PRECISE));
    }

    private String bearerToken(AppUtilisateur user) {
        UtilisateurSecurite principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }
}
