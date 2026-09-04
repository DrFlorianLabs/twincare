package backend.demo.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import backend.demo.application.DemoScenarioService;

@SpringBootTest(properties = {
        "spring.config.location=file:src/main/resources/application.properties,file:src/main/resources/application-demo.properties",
        "jwt.secret=synthetic-demo-test-signing-secret-at-least-32-bytes",
        "app.preparation.token-pepper=synthetic-demo-test-pepper-at-least-32-bytes",
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:twincare_demo_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoApiIntegrationTest {

    private static final String FIRST_REASON =
            "Exemple fictif : gêne au genou droit après une promenade.";
    private static final String CORRECTED_REASON =
            "Exemple fictif corrigé : gêne au genou gauche après une promenade.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoScenarioService scenario;

    @AfterEach
    void resetDemo() {
        scenario.reset();
    }

    @Test
    void pageClearlyIdentifiesSyntheticLocalMode() throws Exception {
        mockMvc.perform(get("/demo/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(content().string(containsString("Données fictives uniquement")))
                .andExpect(content().string(containsString("moins de 10 minutes")));
    }

    @Test
    void patientCorrectionProfessionalReviewAndPortableExportWorkEndToEnd() throws Exception {
        mockMvc.perform(get("/demo/api/state"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.schemaVersion").value("twincare.clinical-context.v1"))
                .andExpect(jsonPath("$.readiness").value("INCOMPLETE"))
                .andExpect(jsonPath("$.facts").isEmpty())
                .andExpect(jsonPath("$.patientReference").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist());

        putReason(FIRST_REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness").value("COMPLETE"))
                .andExpect(jsonPath("$.facts.length()").value(1))
                .andExpect(jsonPath("$.facts[0].value.text").value(FIRST_REASON))
                .andExpect(jsonPath("$.facts[0].provenance.type").value("PATIENT_DECLARED"))
                .andExpect(jsonPath("$.facts[0].certainty").value("NOT_ASSESSED"))
                .andExpect(jsonPath("$.facts[0].review.status").value("UNREVIEWED"));

        putReason(FIRST_REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts.length()").value(1));

        String correctedBody = putReason(CORRECTED_REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts.length()").value(2))
                .andExpect(jsonPath("$.facts[0].lifecycle.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.facts[1].lifecycle.status").value("CURRENT"))
                .andExpect(jsonPath("$.facts[1].value.text").value(CORRECTED_REASON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode correctedState = objectMapper.readTree(correctedBody);
        String currentFactId = correctedState.path("facts").get(1).path("id").asText();

        mockMvc.perform(put("/demo/api/facts/{factId}/review/CONFIRM", currentFactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts[1].review.status").value("CONFIRMED"));
        mockMvc.perform(put("/demo/api/facts/{factId}/review/CONFIRM", currentFactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts[1].review.status").value("CONFIRMED"));
        mockMvc.perform(put("/demo/api/facts/{factId}/review/REJECT", currentFactId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/demo/api/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=twincare-clinical-context-v1.json"))
                .andExpect(jsonPath("$.schemaVersion").value("twincare.clinical-context.v1"))
                .andExpect(jsonPath("$.facts.length()").value(2));

        mockMvc.perform(post("/demo/api/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness").value("INCOMPLETE"))
                .andExpect(jsonPath("$.facts").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions putReason(String reason) throws Exception {
        return mockMvc.perform(put("/demo/api/reason")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DemoReasonRequest(reason))));
    }
}
