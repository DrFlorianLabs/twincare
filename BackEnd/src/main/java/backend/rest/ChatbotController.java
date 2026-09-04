package backend.rest;

import backend.dto.ChatbotAnswerRequest;
import backend.dto.ChatbotSessionResponse;
import backend.entity.InteractionChatbot;
import backend.service.ChatbotSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Profile("legacy-prototype")
@RestController
@RequestMapping("/api/chatbot")
@Validated
public class ChatbotController {

    private final ChatbotSessionService chatbotSessionService;

    public ChatbotController(ChatbotSessionService chatbotSessionService) {
        this.chatbotSessionService = chatbotSessionService;
    }

    
    @GetMapping("/session/patient/{patientId}")
    public ChatbotSessionResponse demarrerSession(@PathVariable Long patientId,
                                                  @RequestParam(defaultValue = "2") @Min(1) @Max(5) int limit) {
        return chatbotSessionService.demarrerSession(patientId, limit);
    }

    @PostMapping("/session/patient/{patientId}/dossier/{dossierId}/answer")
    public InteractionChatbot enregistrerReponse(@PathVariable Long patientId,
                                                 @PathVariable Long dossierId,
                                                 @Valid @RequestBody ChatbotAnswerRequest request) {
        return chatbotSessionService.enregistrerReponse(patientId, dossierId, request);
    }
}
