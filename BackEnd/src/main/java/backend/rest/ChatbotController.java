package backend.rest;

import backend.dto.ChatbotAnswerRequest;
import backend.dto.ChatbotSessionResponse;
import backend.entity.InteractionChatbot;
import backend.service.ChatbotSessionService;
import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("legacy-prototype")
@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotSessionService chatbotSessionService;

    public ChatbotController(ChatbotSessionService chatbotSessionService) {
        this.chatbotSessionService = chatbotSessionService;
    }

    
    @GetMapping("/session/patient/{patientId}")
    public ChatbotSessionResponse demarrerSession(@PathVariable Long patientId,
                                                  @RequestParam(defaultValue = "2") int limit) {
        // Sécurité backend : on ne dépasse pas 5 questions
        if (limit > 5) {
            limit = 5;
        }
        return chatbotSessionService.demarrerSession(patientId, limit);
    }

    @PostMapping("/session/patient/{patientId}/dossier/{dossierId}/answer")
    public InteractionChatbot enregistrerReponse(@PathVariable Long patientId,
                                                 @PathVariable Long dossierId,
                                                 @Valid @RequestBody ChatbotAnswerRequest request) {
        return chatbotSessionService.enregistrerReponse(patientId, dossierId, request);
    }
}
