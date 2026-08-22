package backend.preparation.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ProfessionalContextApiSecurityIntegrationTest {

    private static final String ENDPOINT = "/api/v1/professional-context";
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppUtilisateurRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearData();
        addOrganization("cabinet-a", "Cabinet Alpha");
        addOrganization("cabinet-b", "Cabinet Bêta");
        addOrganization("cabinet-inactive", "Cabinet Inactif");
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void physicianReceivesOnlyActiveClinicalOrganizationsAndServerDerivedCapabilities() throws Exception {
        AppUtilisateur physician = createUser("physician-context@synthetic.invalid", Role.MEDECIN, "Médecin");
        addMembership("cabinet-a", physician, "CLINICIAN", true);
        addMembership("cabinet-b", physician, "MEDICAL_ASSISTANT", true);
        addMembership("cabinet-inactive", physician, "CLINICIAN", false);

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.actorUserId").value(physician.getId()))
                .andExpect(jsonPath("$.displayName").value("Médecin Synthetic"))
                .andExpect(jsonPath("$.organizations.length()").value(2))
                .andExpect(jsonPath("$.organizations[0].organizationId").value("cabinet-a"))
                .andExpect(jsonPath("$.organizations[0].displayName").value("Cabinet Alpha"))
                .andExpect(jsonPath("$.organizations[0].capabilities.length()").value(6))
                .andExpect(jsonPath("$.organizations[0].capabilities[0]").value("VIEW_PREPARATION"))
                .andExpect(jsonPath("$.organizations[0].capabilities[3]")
                        .value("CONFIRM_READY_FOR_CONSULTATION"))
                .andExpect(jsonPath("$.organizations[0].capabilities[4]")
                        .value("CLOSE_PREPARATION_CASE"))
                .andExpect(jsonPath("$.organizations[0].capabilities[5]")
                        .value("VIEW_PREPARATION_AUDIT"))
                .andExpect(jsonPath("$.organizations[1].organizationId").value("cabinet-b"))
                .andExpect(jsonPath("$.organizations[1].capabilities.length()").value(3));
    }

    @Test
    void assistantNeverReceivesReadyConfirmationCapability() throws Exception {
        AppUtilisateur assistant = createUser(
                "assistant-context@synthetic.invalid", Role.ASSISTANT_MEDICAL, "Assistant");
        addMembership("cabinet-a", assistant, "CLINICIAN", true);

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearerToken(assistant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations.length()").value(1))
                .andExpect(jsonPath("$.organizations[0].capabilities.length()").value(3))
                .andExpect(jsonPath("$.organizations[0].capabilities",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                "CONFIRM_READY_FOR_CONSULTATION"))))
                .andExpect(jsonPath("$.organizations[0].capabilities",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                "CLOSE_PREPARATION_CASE"))))
                .andExpect(jsonPath("$.organizations[0].capabilities",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                "VIEW_PREPARATION_AUDIT"))));
    }

    @Test
    void organizationAdministratorMembershipDoesNotGrantClinicalContext() throws Exception {
        AppUtilisateur physician = createUser(
                "organization-admin-context@synthetic.invalid", Role.MEDECIN, "Médecin");
        addMembership("cabinet-a", physician, "ORGANIZATION_ADMIN", true);

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearerToken(physician)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations.length()").value(0));
    }

    @Test
    void administratorPatientAndAnonymousRequestsFailClosedWithNoStore() throws Exception {
        AppUtilisateur administrator = createUser("admin-context@synthetic.invalid", Role.ADMIN, "Admin");
        AppUtilisateur patient = createUser("patient-context@synthetic.invalid", Role.PATIENT, "Patient");
        addMembership("cabinet-a", administrator, "ORGANIZATION_ADMIN", true);

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearerToken(administrator)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get(ENDPOINT).header("Authorization", bearerToken(patient)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    private AppUtilisateur createUser(String email, Role role, String firstName) {
        return userRepository.save(new AppUtilisateur(
                "Synthetic",
                firstName,
                LocalDate.of(1990, 1, 1),
                email,
                passwordEncoder.encode("synthetic-password"),
                role,
                Sexe.NON_PRECISE));
    }

    private void addOrganization(String id, String displayName) {
        jdbcTemplate.update(
                "INSERT INTO care_organization (id, display_name) VALUES (?, ?)",
                id,
                displayName);
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
        var principal = new UtilisateurSecurite(
                user.getEmail(),
                user.getMotDePasse(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        return "Bearer " + jwtService.generateToken(principal);
    }

    private void clearData() {
        jdbcTemplate.update("DELETE FROM organization_membership");
        jdbcTemplate.update("DELETE FROM care_organization");
        userRepository.deleteAll();
    }
}
