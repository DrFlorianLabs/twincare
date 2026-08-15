package backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dao.AppUtilisateurRepository;
import backend.dao.DossierPatientRepository;
import backend.dao.InteractionChatbotRepository;
import backend.dto.ChatbotAnswerRequest;
import backend.dto.ChatbotQuestionDto;
import backend.dto.ChatbotSessionResponse;
import backend.entity.AppUtilisateur;
import backend.entity.DossierPatient;
import backend.entity.InteractionChatbot;
import backend.entity.PatientProfile;
import backend.entity.QuestionDefinition;
import backend.entity.TypeReponseChatbot;
import backend.exception.BadRequestException;
import backend.exception.ResourceNotFoundException;

@Service
public class ChatbotSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotSessionService.class);

    private final AppUtilisateurRepository appUtilisateurRepository;
    private final DossierPatientRepository dossierPatientRepository;
    private final InteractionChatbotRepository interactionChatbotRepository;
    private final ChatbotTriggerEngineService triggerEngineService;
    private final ChatbotQuestionCatalogService catalogService;
    private final SecurityHelperService securityHelperService;

    public ChatbotSessionService(
            AppUtilisateurRepository appUtilisateurRepository,
            DossierPatientRepository dossierPatientRepository,
            InteractionChatbotRepository interactionChatbotRepository,
            ChatbotTriggerEngineService triggerEngineService,
            ChatbotQuestionCatalogService catalogService,
            SecurityHelperService securityHelperService) {
        this.appUtilisateurRepository = appUtilisateurRepository;
        this.dossierPatientRepository = dossierPatientRepository;
        this.interactionChatbotRepository = interactionChatbotRepository;
        this.triggerEngineService = triggerEngineService;
        this.catalogService = catalogService;
        this.securityHelperService = securityHelperService;
    }

    public ChatbotSessionResponse demarrerSession(Long patientId, int limit) {
        securityHelperService.checkPatientAccess(patientId);
        AppUtilisateur patient = appUtilisateurRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient introuvable"));
        DossierPatient dossier = dossierPatientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable"));

        PatientProfile profile = buildLegacyPatientProfile(patient, dossier);
        Map<Integer, LocalDateTime> lastAnswerDates = new HashMap<>();
        List<QuestionDefinition> allQuestions = catalogService.getAllQuestions();
        for (QuestionDefinition question : allQuestions) {
            interactionChatbotRepository.findLastAnswerDateByDossierAndQuestionId(dossier.getId(), question.getId())
                    .ifPresent(date -> lastAnswerDates.put(question.getId(), date));
        }

        List<ChatbotQuestionDto> questions = triggerEngineService.selectQuestions(profile, lastAnswerDates, limit);

        ChatbotSessionResponse response = new ChatbotSessionResponse();
        response.setPatientId(patientId);
        response.setDossierId(dossier.getId());
        response.setQuestions(questions);
        return response;
    }

    @Transactional
    public InteractionChatbot enregistrerReponse(Long patientId, Long dossierId, ChatbotAnswerRequest request) {
        securityHelperService.checkPatientAccess(patientId);
        if (request == null || request.getQuestionId() == null) {
            throw new BadRequestException("Question obligatoire");
        }

        AppUtilisateur patient = appUtilisateurRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient introuvable"));
        DossierPatient dossier = dossierPatientRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable"));

        if (!dossier.getPatient().getId().equals(patientId)) {
            throw new BadRequestException("Le dossier n'appartient pas au patient");
        }

        // Never trust question text/category/type echoed by the browser. The
        // server-side catalog is the canonical source for persisted metadata.
        QuestionDefinition question = catalogService.getQuestionById(request.getQuestionId());
        TypeReponseChatbot type = question.getTypeReponse();

        InteractionChatbot interaction = new InteractionChatbot();
        interaction.setPatient(patient);
        interaction.setDossierPatient(dossier);
        interaction.setQuestionId(question.getId());
        interaction.setQuestion(question.getQuestion());
        interaction.setCategorie(question.getCategorie());
        interaction.setTypeReponse(type);

        if (type == TypeReponseChatbot.OUI_NON) {
            String answer = request.getReponseTexte();
            if (answer == null
                    || (!"OUI".equalsIgnoreCase(answer)
                    && !"NON".equalsIgnoreCase(answer)
                    && !"JE_NE_SAIS_PAS".equalsIgnoreCase(answer))) {
                throw new BadRequestException("Réponse OUI/NON/JE_NE_SAIS_PAS invalide");
            }
            interaction.setReponseTexte(answer.toUpperCase());
        } else if (type == TypeReponseChatbot.ECHELLE_1_5) {
            Integer answer = request.getReponseNumerique();
            if (answer == null || answer < 1 || answer > 5) {
                throw new BadRequestException("La réponse doit être comprise entre 1 et 5");
            }
            interaction.setReponseNumerique(answer);
        } else if (type == TypeReponseChatbot.ECHELLE_0_7) {
            Integer answer = request.getReponseNumerique();
            if (answer == null || answer < 0 || answer > 7) {
                throw new BadRequestException("La réponse doit être comprise entre 0 et 7");
            }
            interaction.setReponseNumerique(answer);
        } else {
            throw new BadRequestException("Type de réponse non pris en charge");
        }

        // The prototype's former generic thresholds were not clinically
        // validated. Do not infer a need for professional review from them.
        // Future review/tasks belong to typed, versioned clinical rules.
        interaction.setARevoirParProfessionnel(false);

        InteractionChatbot saved = interactionChatbotRepository.save(interaction);
        log.info("Legacy preparation answer recorded");
        return saved;
    }

    /**
     * Compatibility profile for the legacy question engine only. Do not add
     * new clinical inference here: the V0.1 rule model will replace it.
     */
    private PatientProfile buildLegacyPatientProfile(AppUtilisateur patient, DossierPatient dossier) {
        PatientProfile profile = new PatientProfile();
        profile.setPatientId(patient.getId());
        profile.setAge(Period.between(patient.getDateNaissance(), LocalDate.now()).getYears());
        profile.setSexe(patient.getSexe());
        profile.setDossierIncomplet(dossier.getDocuments() == null || dossier.getDocuments().isEmpty());
        profile.setSaison(getCurrentSeason());

        // Historical inference used unrelated question/category IDs to derive
        // allergies, sleep and stress. Until clinically re-specified, leave
        // those fields unknown instead of manufacturing a value.
        profile.setAllergiesConnues(false);
        profile.setSommeilScore(null);
        profile.setStressScore(null);
        return profile;
    }

    private String getCurrentSeason() {
        Month month = LocalDate.now().getMonth();
        return switch (month) {
            case MARCH, APRIL, MAY -> "PRINTEMPS";
            case JUNE, JULY, AUGUST -> "ETE";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "AUTOMNE";
            default -> "HIVER";
        };
    }
}
