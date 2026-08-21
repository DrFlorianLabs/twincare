package backend.preparation.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.exception.BadRequestException;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentCommand;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentUseCase;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases/{caseId}/attachment")
public class ProfessionalAttachmentDownloadController {

    private static final Logger log = LoggerFactory.getLogger(ProfessionalAttachmentDownloadController.class);
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private final DownloadProfessionalAttachmentUseCase downloadAttachment;

    public ProfessionalAttachmentDownloadController(
            DownloadProfessionalAttachmentUseCase downloadAttachment) {
        this.downloadAttachment = downloadAttachment;
    }

    @GetMapping
    public void download(
            @PathVariable String organizationId,
            @PathVariable String caseId,
            HttpServletResponse response) {
        DownloadProfessionalAttachmentCommand command;
        try {
            command = new DownloadProfessionalAttachmentCommand(
                    new OrganizationId(organizationId),
                    new PreparationCaseId(UUID.fromString(caseId)));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Identifiant de préparation invalide");
        }
        var result = downloadAttachment.download(command);
        try (var content = result.content()) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                            .filename(result.displayFilename(), StandardCharsets.UTF_8)
                            .build()
                            .toString());
            response.setHeader(X_CONTENT_TYPE_OPTIONS, "nosniff");
            response.setContentType(result.mediaType().contentType());
            response.setContentLengthLong(result.sizeBytes());
            try {
                content.transferTo(response.getOutputStream());
            } catch (java.io.IOException exception) {
                // The response may already be committed. Do not attempt to replace it with a
                // misleading JSON 500 response; the try-with-resources still closes the handle.
                log.debug(
                        "Professional attachment response stream interrupted type={}",
                        exception.getClass().getSimpleName());
            }
        }
    }
}
