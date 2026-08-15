package backend.rest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import backend.dto.DocumentContent;
import backend.dto.DocumentUploadRequest;
import backend.entity.Document;
import backend.entity.TypeDocument;
import backend.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload/dossier/{dossierId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Document uploadDocument(
            @PathVariable Long dossierId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") TypeDocument type,
            @RequestParam("dateDocument")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDocument,
            @RequestParam(value = "prescripteur", required = false) String prescripteur,
            @RequestParam(value = "commentairePatient", required = false) String commentairePatient) {

        DocumentUploadRequest request = new DocumentUploadRequest();
        request.setType(type);
        request.setDateDocument(dateDocument);
        request.setPrescripteur(prescripteur);
        request.setCommentairePatient(commentairePatient);

        return documentService.uploadDocument(dossierId, file, request);
    }

    @GetMapping("/dossier/{dossierId}")
    public List<Document> getDocumentsByDossier(@PathVariable Long dossierId) {
        return documentService.getDocumentsByDossier(dossierId);
    }

    @GetMapping("/{documentId}")
    public Document getDocumentById(@PathVariable Long documentId) {
        return documentService.getById(documentId);
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> getDocumentContent(@PathVariable Long documentId) {
        DocumentContent content = documentService.getContent(documentId);

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
