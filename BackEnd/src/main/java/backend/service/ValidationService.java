package backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dao.DocumentRepository;
import backend.dao.ValidationRepository;
import backend.dto.ValidationRequest;
import backend.entity.AppUtilisateur;
import backend.entity.DecisionValidation;
import backend.entity.Document;
import backend.entity.Role;
import backend.entity.StatutDocument;
import backend.entity.Validation;
import backend.exception.BadRequestException;
import backend.exception.ResourceNotFoundException;
import backend.exception.UnauthorizedActionException;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final ValidationRepository validationRepository;
    private final DocumentRepository documentRepository;
    private final DossierPatientService dossierPatientService;
    private final SecurityHelperService securityHelperService;

    public ValidationService(
            ValidationRepository validationRepository,
            DocumentRepository documentRepository,
            DossierPatientService dossierPatientService,
            SecurityHelperService securityHelperService) {
        this.validationRepository = validationRepository;
        this.documentRepository = documentRepository;
        this.dossierPatientService = dossierPatientService;
        this.securityHelperService = securityHelperService;
    }

    @Transactional
    public Validation validerDocument(Long documentId, ValidationRequest request) {
        AppUtilisateur actor = securityHelperService.getCurrentUser();
        if (actor.getRole() != Role.ASSISTANT_MEDICAL && actor.getRole() != Role.MEDECIN) {
            throw new UnauthorizedActionException("Seul un assistant médical ou un médecin peut valider un document");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));
        securityHelperService.checkDocumentAccess(document);

        if (document.getValidation() != null) {
            throw new BadRequestException("Ce document a déjà été validé");
        }
        if (request == null || request.getDecision() == null) {
            throw new BadRequestException("Décision de validation obligatoire");
        }

        Validation validation = new Validation();
        validation.setDocument(document);
        validation.setAssistantMedical(actor);
        validation.setDecision(request.getDecision());
        validation.setCommentaire(request.getCommentaire());

        if (request.getDecision() == DecisionValidation.VALIDER) {
            document.setStatut(StatutDocument.VALIDE);
        } else if (request.getDecision() == DecisionValidation.REFUSER) {
            document.setStatut(StatutDocument.REFUSE);
        } else {
            throw new BadRequestException("Décision de validation invalide");
        }

        documentRepository.save(document);
        Validation savedValidation = validationRepository.save(validation);
        dossierPatientService.mettreAJourStatutDossier(document.getDossierPatient().getId());
        log.info("Document review recorded");
        return savedValidation;
    }
}
