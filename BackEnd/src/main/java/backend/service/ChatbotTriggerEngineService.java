package backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import backend.dto.ChatbotQuestionDto;
import backend.entity.PatientProfile;
import backend.entity.Priorite;
import backend.entity.QuestionDefinition;

@Service
public class ChatbotTriggerEngineService {

    private final ChatbotQuestionCatalogService catalogService;

    public ChatbotTriggerEngineService(ChatbotQuestionCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public List<ChatbotQuestionDto> selectQuestions(
            PatientProfile profile,
            Map<Integer, LocalDateTime> lastAnswerDates,
            int limit) {
        if (profile == null || limit <= 0) {
            return List.of();
        }

        List<QuestionDefinition> allQuestions = new ArrayList<>(catalogService.getAllQuestions());
        allQuestions.sort(Comparator.comparingInt(this::priorityRank));

        List<ChatbotQuestionDto> selected = new ArrayList<>();
        for (QuestionDefinition question : allQuestions) {
            LocalDateTime lastDate = lastAnswerDates != null ? lastAnswerDates.get(question.getId()) : null;
            if (matchesTrigger(question, profile, lastDate)) {
                selected.add(new ChatbotQuestionDto(
                        question.getId(),
                        question.getCategorie(),
                        question.getQuestion(),
                        question.getTypeReponse()));
            }
            if (selected.size() >= limit) {
                break;
            }
        }
        return selected;
    }

    private int priorityRank(QuestionDefinition question) {
        Priorite priority = question.getPriorite();
        if (priority == null) {
            return 4;
        }
        return switch (priority) {
            case HAUTE -> 1;
            case MOYENNE -> 2;
            case FAIBLE -> 3;
        };
    }

    private boolean matchesTrigger(
            QuestionDefinition question,
            PatientProfile profile,
            LocalDateTime lastAnswerDate) {
        String trigger = question.getDeclencheur();

        if (trigger != null && trigger.startsWith("Tous les")) {
            long months = extractMonths(trigger);
            if (months <= 0) {
                return false;
            }
            return lastAnswerDate == null
                    || !lastAnswerDate.plusMonths(months).isAfter(LocalDateTime.now());
        }

        if (lastAnswerDate != null) {
            return false;
        }

        if (trigger == null || trigger.isBlank() || "Aucun".equalsIgnoreCase(trigger)) {
            return true;
        }

        if ("Femme > 50 ans".equalsIgnoreCase(trigger)) {
            return profile.getSexe() != null
                    && "FEMME".equalsIgnoreCase(profile.getSexe().toString())
                    && profile.getAge() != null
                    && profile.getAge() > 50;
        }

        if ("Femme 25-65 ans".equalsIgnoreCase(trigger)) {
            return profile.getSexe() != null
                    && "FEMME".equalsIgnoreCase(profile.getSexe().toString())
                    && profile.getAge() != null
                    && profile.getAge() >= 25
                    && profile.getAge() <= 65;
        }

        if ("Age = 25, 45, 65 ans".equalsIgnoreCase(trigger)) {
            return profile.getAge() != null
                    && (profile.getAge() == 25 || profile.getAge() == 45 || profile.getAge() == 65);
        }

        if ("Age > 65 ans".equalsIgnoreCase(trigger)) {
            return profile.getAge() != null && profile.getAge() > 65;
        }

        if ("Age > 65 ans + Automne".equalsIgnoreCase(trigger)) {
            return profile.getAge() != null
                    && profile.getAge() > 65
                    && "AUTOMNE".equalsIgnoreCase(profile.getSaison());
        }

        if ("Si allergies = Oui".equalsIgnoreCase(trigger)) {
            return profile.isAllergiesConnues();
        }

        if ("Si sommeil < 3".equalsIgnoreCase(trigger)) {
            return profile.getSommeilScore() != null && profile.getSommeilScore() < 3;
        }

        if ("Si stress > 3".equalsIgnoreCase(trigger)) {
            return profile.getStressScore() != null && profile.getStressScore() > 3;
        }

        // The current PatientProfile does not contain a validated snoring field.
        // A trigger that cannot be evaluated safely must not fire.
        if ("Si ronflement = Oui".equalsIgnoreCase(trigger)) {
            return false;
        }

        if ("Dossier incomplet".equalsIgnoreCase(trigger)) {
            return profile.isDossierIncomplet();
        }

        if ("Printemps".equalsIgnoreCase(trigger)) {
            return "PRINTEMPS".equalsIgnoreCase(profile.getSaison());
        }

        // Fail closed: an unknown or malformed clinical trigger is never treated
        // as applicable by default.
        return false;
    }

    private long extractMonths(String trigger) {
        if (trigger.contains("3 mois")) {
            return 3;
        }
        if (trigger.contains("6 mois")) {
            return 6;
        }
        if (trigger.contains("3 ans")) {
            return 36;
        }
        if (trigger.contains("2 ans")) {
            return 24;
        }
        if (trigger.contains("Tous les ans") || trigger.contains("1 an")) {
            return 12;
        }
        return -1;
    }
}
