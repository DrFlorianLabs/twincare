package backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import backend.dao.AppUtilisateurRepository;
import backend.entity.AppUtilisateur;
import backend.entity.Document;
import backend.entity.DossierPatient;
import backend.entity.Role;
import backend.exception.ResourceNotFoundException;
import backend.exception.UnauthorizedActionException;

@Service
public class SecurityHelperService {

    private final AppUtilisateurRepository appUtilisateurRepository;

    public SecurityHelperService(AppUtilisateurRepository appUtilisateurRepository) {
        this.appUtilisateurRepository = appUtilisateurRepository;
    }

    public AppUtilisateur getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new UnauthorizedActionException("Utilisateur non authentifié");
        }

        return appUtilisateurRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    public void checkPatientAccess(Long patientId) {
        if (patientId == null) {
            throw new UnauthorizedActionException("Accès interdit");
        }

        AppUtilisateur currentUser = getCurrentUser();
        if (isStaff(currentUser.getRole())) {
            return;
        }

        if (currentUser.getRole() != Role.PATIENT || !currentUser.getId().equals(patientId)) {
            throw new UnauthorizedActionException("Accès interdit");
        }
    }

    public void checkDossierAccess(DossierPatient dossier) {
        if (dossier == null || dossier.getPatient() == null || dossier.getPatient().getId() == null) {
            throw new UnauthorizedActionException("Accès interdit");
        }
        checkPatientAccess(dossier.getPatient().getId());
    }

    public void checkDocumentAccess(Document document) {
        if (document == null || document.getDossierPatient() == null) {
            throw new UnauthorizedActionException("Accès interdit");
        }
        checkDossierAccess(document.getDossierPatient());
    }

    public void requireStaff() {
        AppUtilisateur currentUser = getCurrentUser();
        if (!isStaff(currentUser.getRole())) {
            throw new UnauthorizedActionException("Accès réservé aux professionnels autorisés");
        }
    }

    public void requireAdmin() {
        AppUtilisateur currentUser = getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedActionException("Accès réservé aux administrateurs");
        }
    }

    private boolean isStaff(Role role) {
        return role == Role.ASSISTANT_MEDICAL || role == Role.MEDECIN || role == Role.ADMIN;
    }
}
