package backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dao.DocumentRepository;
import backend.dao.DossierPatientRepository;
import backend.entity.Document;
import backend.entity.DossierPatient;
import backend.entity.StatutDocument;
import backend.entity.StatutDossier;
import backend.exception.ResourceNotFoundException;

@Service
public class DossierPatientService {

    private final DossierPatientRepository dossierPatientRepository;
    private final DocumentRepository documentRepository;
    private final SecurityHelperService securityHelperService;

    public DossierPatientService(
            DossierPatientRepository dossierPatientRepository,
            DocumentRepository documentRepository,
            SecurityHelperService securityHelperService) {
        this.dossierPatientRepository = dossierPatientRepository;
        this.documentRepository = documentRepository;
        this.securityHelperService = securityHelperService;
    }

    @Transactional(readOnly = true)
    public DossierPatient getByPatientId(Long patientId) {
        securityHelperService.checkPatientAccess(patientId);
        return dossierPatientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier patient introuvable"));
    }

    @Transactional(readOnly = true)
    public DossierPatient getById(Long dossierId) {
        DossierPatient dossier = dossierPatientRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable"));
        securityHelperService.checkDossierAccess(dossier);
        return dossier;
    }

    @Transactional
    public void mettreAJourStatutDossier(Long dossierId) {
        DossierPatient dossier = getById(dossierId);
        List<Document> documents = documentRepository.findByDossierPatientId(dossierId);

        if (documents.isEmpty()) {
            dossier.setStatut(StatutDossier.A_COMPLETER);
        } else if (documents.stream().allMatch(doc -> doc.getStatut() == StatutDocument.VALIDE)) {
            dossier.setStatut(StatutDossier.PRET_POUR_CONSULTATION);
        } else {
            dossier.setStatut(StatutDossier.EN_COURS);
        }

        dossierPatientRepository.save(dossier);
    }

    @Transactional(readOnly = true)
    public List<DossierPatient> getAll() {
        securityHelperService.requireStaff();
        return dossierPatientRepository.findAll();
    }
}
