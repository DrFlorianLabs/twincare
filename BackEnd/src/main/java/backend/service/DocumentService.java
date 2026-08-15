package backend.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import backend.dao.DocumentRepository;
import backend.dao.DossierPatientRepository;
import backend.dto.DocumentContent;
import backend.dto.DocumentUploadRequest;
import backend.entity.Document;
import backend.entity.DossierPatient;
import backend.entity.StatutDocument;
import backend.exception.BadRequestException;
import backend.exception.ResourceNotFoundException;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DossierPatientRepository dossierPatientRepository;
    private final FileStorageService fileStorageService;
    private final DossierPatientService dossierPatientService;
    private final SecurityHelperService securityHelperService;

    public DocumentService(
            DocumentRepository documentRepository,
            DossierPatientRepository dossierPatientRepository,
            FileStorageService fileStorageService,
            DossierPatientService dossierPatientService,
            SecurityHelperService securityHelperService) {
        this.documentRepository = documentRepository;
        this.dossierPatientRepository = dossierPatientRepository;
        this.fileStorageService = fileStorageService;
        this.dossierPatientService = dossierPatientService;
        this.securityHelperService = securityHelperService;
    }

    @Transactional
    public Document uploadDocument(Long dossierId, MultipartFile file, DocumentUploadRequest request) {
        DossierPatient dossier = dossierPatientRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable"));
        securityHelperService.checkDossierAccess(dossier);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Le fichier est obligatoire");
        }
        if (request == null || request.getType() == null) {
            throw new BadRequestException("Le type du document est obligatoire");
        }
        if (request.getDateDocument() == null) {
            throw new BadRequestException("La date du document est obligatoire");
        }

        String storedFilename = fileStorageService.storeFile(file);
        try {
            Document document = new Document();
            document.setType(request.getType());
            document.setDateDocument(request.getDateDocument());
            document.setNomFichier(fileStorageService.sanitizeDisplayFilename(file.getOriginalFilename()));
            document.setCheminFichier(storedFilename);
            document.setPrescripteur(request.getPrescripteur());
            document.setCommentairePatient(request.getCommentairePatient());
            document.setStatut(StatutDocument.EN_ATTENTE);
            document.setDossierPatient(dossier);

            Document saved = documentRepository.save(document);
            dossierPatientService.mettreAJourStatutDossier(dossierId);
            log.info("Document stored");
            return saved;
        } catch (RuntimeException e) {
            try {
                fileStorageService.deleteFile(storedFilename);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    public List<Document> getDocumentsByDossier(Long dossierId) {
        DossierPatient dossier = dossierPatientRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable"));
        securityHelperService.checkDossierAccess(dossier);
        return documentRepository.findByDossierPatientId(dossierId);
    }

    public Document getById(Long documentId) {
        Document document = findDocument(documentId);
        securityHelperService.checkDocumentAccess(document);
        return document;
    }

    public DocumentContent getContent(Long documentId) {
        Document document = findDocument(documentId);
        securityHelperService.checkDocumentAccess(document);

        return new DocumentContent(
                fileStorageService.loadFile(document.getCheminFichier()),
                fileStorageService.contentTypeFor(document.getCheminFichier()),
                document.getNomFichier());
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        Document document = findDocument(documentId);
        securityHelperService.checkDocumentAccess(document);
        Long dossierId = document.getDossierPatient().getId();

        fileStorageService.deleteFile(document.getCheminFichier());
        documentRepository.delete(document);
        documentRepository.flush();
        dossierPatientService.mettreAJourStatutDossier(dossierId);
        log.info("Document deleted");
    }

    private Document findDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));
    }
}
