package backend.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.entity.InteractionChatbot;
import backend.service.InteractionChatbotService;

@RestController
@RequestMapping("/api/interactions")
public class InteractionChatbotController {

    private final InteractionChatbotService interactionService;

    public InteractionChatbotController(InteractionChatbotService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/dossier/{dossierId}")
    public List<InteractionChatbot> getByDossier(@PathVariable Long dossierId) {
        return interactionService.getByDossier(dossierId);
    }
}
