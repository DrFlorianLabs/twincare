package backend.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Document;
import backend.entity.DossierPatient;
import backend.entity.Role;
import backend.exception.BadRequestException;
import backend.exception.ResourceNotFoundException;

@Service
public class UtilisateurService {

    private static final Logger log = LoggerFactory.getLogger(UtilisateurService.class);

    private final AppUtilisateurRepository utilisateurRepository;
    private final SecurityHelperService securityHelperService;
    private final FileTransactionService fileTransactionService;

    public UtilisateurService(
            AppUtilisateurRepository utilisateurRepository,
            SecurityHelperService securityHelperService,
            FileTransactionService fileTransactionService) {
        this.utilisateurRepository = utilisateurRepository;
        this.securityHelperService = securityHelperService;
        this.fileTransactionService = fileTransactionService;
    }

    @Transactional(readOnly = true)
    public List<AppUtilisateur> getAllUsers() {
        securityHelperService.requireStaff();
        return utilisateurRepository.findAll();
    }

    @Transactional
    public void deletePatient(Long userId) {
        securityHelperService.requireAdmin();
        AppUtilisateur user = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        if (user.getRole() != Role.PATIENT) {
            throw new BadRequestException("Cet endpoint supprime uniquement les comptes patients");
        }

        DossierPatient dossier = user.getDossierPatient();
        if (dossier != null && dossier.getDocuments() != null) {
            for (Document document : dossier.getDocuments()) {
                fileTransactionService.stageDeletionAfterCommit(document.getCheminFichier());
            }
        }

        utilisateurRepository.delete(user);
        utilisateurRepository.flush();
        log.info("Patient account deleted");
    }
}
