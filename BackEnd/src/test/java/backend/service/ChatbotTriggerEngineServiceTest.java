package backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import backend.entity.CategorieChatbot;
import backend.entity.PatientProfile;
import backend.entity.Priorite;
import backend.entity.QuestionDefinition;
import backend.entity.TypeReponseChatbot;

@ExtendWith(MockitoExtension.class)
class ChatbotTriggerEngineServiceTest {

    @Mock
    private ChatbotQuestionCatalogService catalogService;

    private ChatbotTriggerEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new ChatbotTriggerEngineService(catalogService);
    }

    @Test
    void unknownTriggerFailsClosed() {
        when(catalogService.getAllQuestions()).thenReturn(List.of(question(1, "Règle inconnue")));

        var selected = engine.selectQuestions(new PatientProfile(), Map.of(), 5);

        assertThat(selected).isEmpty();
    }

    @Test
    void unsupportedSnoringTriggerDoesNotFire() {
        when(catalogService.getAllQuestions()).thenReturn(List.of(question(1, "Si ronflement = Oui")));

        var selected = engine.selectQuestions(new PatientProfile(), Map.of(), 5);

        assertThat(selected).isEmpty();
    }

    @Test
    void twoYearRecurrenceDoesNotFireAfterOnlyEighteenMonths() {
        when(catalogService.getAllQuestions()).thenReturn(List.of(question(1, "Tous les 2 ans")));

        var selected = engine.selectQuestions(
                new PatientProfile(),
                Map.of(1, LocalDateTime.now().minusMonths(18)),
                5);

        assertThat(selected).isEmpty();
    }

    @Test
    void twoYearRecurrenceFiresAfterTwentyFiveMonths() {
        when(catalogService.getAllQuestions()).thenReturn(List.of(question(1, "Tous les 2 ans")));

        var selected = engine.selectQuestions(
                new PatientProfile(),
                Map.of(1, LocalDateTime.now().minusMonths(25)),
                5);

        assertThat(selected).hasSize(1);
    }

    @Test
    void malformedRecurrenceFailsClosed() {
        when(catalogService.getAllQuestions()).thenReturn(List.of(question(1, "Tous les parfois")));

        var selected = engine.selectQuestions(
                new PatientProfile(),
                Map.of(1, LocalDateTime.now().minusYears(10)),
                5);

        assertThat(selected).isEmpty();
    }

    private QuestionDefinition question(int id, String trigger) {
        return new QuestionDefinition(
                id,
                CategorieChatbot.MOTIF_CONSULTATION,
                "Question synthétique de test",
                TypeReponseChatbot.OUI_NON,
                trigger,
                Priorite.HAUTE);
    }
}
