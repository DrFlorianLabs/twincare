package backend.preparation.adapter.in.web;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import backend.preparation.application.InvalidPatientAttachmentException;
import backend.preparation.application.port.in.DeletePatientAttachmentCommand;
import backend.preparation.application.port.in.DeletePatientAttachmentUseCase;
import backend.preparation.application.port.in.GetPatientAttachmentCommand;
import backend.preparation.application.port.in.GetPatientAttachmentUseCase;
import backend.preparation.application.port.in.PatientAttachmentMetadataResult;
import backend.preparation.application.port.in.PutPatientAttachmentCommand;
import backend.preparation.application.port.in.PutPatientAttachmentResult;
import backend.preparation.application.port.in.PutPatientAttachmentUseCase;

@RestController
@RequestMapping("/api/v1/patient-access/attachment")
public class PatientAttachmentController {

    private final PutPatientAttachmentUseCase putAttachment;
    private final GetPatientAttachmentUseCase getAttachment;
    private final DeletePatientAttachmentUseCase deleteAttachment;

    public PatientAttachmentController(
            PutPatientAttachmentUseCase putAttachment,
            GetPatientAttachmentUseCase getAttachment,
            DeletePatientAttachmentUseCase deleteAttachment) {
        this.putAttachment = putAttachment;
        this.getAttachment = getAttachment;
        this.deleteAttachment = deleteAttachment;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PatientAttachmentResponse> put(
            @AuthenticationPrincipal PatientPreparationPrincipal principal,
            MultipartHttpServletRequest request) {
        MultipartFile file = requireSingleFilePart(request);
        PutPatientAttachmentResult result = putAttachment.put(new PutPatientAttachmentCommand(
                principal.grant(), new MultipartPatientAttachmentUpload(file)));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .body(response(result));
    }

    @GetMapping
    public ResponseEntity<PatientAttachmentResponse> get(
            @AuthenticationPrincipal PatientPreparationPrincipal principal) {
        PatientAttachmentMetadataResult result = getAttachment.get(
                new GetPatientAttachmentCommand(principal.grant()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .body(new PatientAttachmentResponse(
                        result.displayFilename(),
                        result.mediaType().contentType(),
                        result.sizeBytes(),
                        result.uploadedAt(),
                        result.updatedAt()));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal PatientPreparationPrincipal principal) {
        deleteAttachment.delete(new DeletePatientAttachmentCommand(principal.grant()));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    private MultipartFile requireSingleFilePart(MultipartHttpServletRequest request) {
        var files = request.getMultiFileMap();
        List<MultipartFile> fileParts = files.get("file");
        if (files.size() != 1
                || fileParts == null
                || fileParts.size() != 1
                || !request.getParameterMap().isEmpty()) {
            throw new InvalidPatientAttachmentException(
                    InvalidPatientAttachmentException.Reason.BAD_REQUEST);
        }
        return fileParts.getFirst();
    }

    private PatientAttachmentResponse response(PutPatientAttachmentResult result) {
        return new PatientAttachmentResponse(
                result.displayFilename(),
                result.mediaType().contentType(),
                result.sizeBytes(),
                result.uploadedAt(),
                result.updatedAt());
    }
}
