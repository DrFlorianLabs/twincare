package backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dao.DossierPatientRepository;
import backend.dao.InteractionChatbotRepository;
import backend.entity.DossierPatient;
import backend.entity.InteractionChatbot;
import backend.exception.ResourceNotFoundException;

@Service
public class InteractionChatbotService {

    private final InteractionChatbotRepository interactionRepository;
    private final DossierPatientRepository dossierRepository;
    private final SecurityHelperService securityHelperService;

    public InteractionChatbotService(
            InteractionChatbotRepository interactionRepository,
            DossierPatientRepository dossierRepository,
            SecurityHelperService securityHelperService) {
        this.interactionRepository = interactionRepository;
        this.dossierRepository = dossierRepository;
        this.securityHelperService = securityHelperService;
    }

    @Transactional(readOnly = true)
    public List<InteractionChatbot> getByDossier(Long dossierId) {
        securityHelperService.requireStaff();
        DossierPatient dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable"));
        securityHelperService.checkDossierAccess(dossier);
        return interactionRepository.findByDossierPatientId(dossierId);
    }
}
