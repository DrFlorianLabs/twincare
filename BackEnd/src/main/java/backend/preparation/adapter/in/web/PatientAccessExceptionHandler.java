package backend.preparation.adapter.in.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import backend.preparation.application.InvalidPatientAttachmentException;
import backend.preparation.application.InvalidPatientReasonCommandException;
import backend.preparation.application.PatientAttachmentNotFoundException;
import backend.preparation.application.PatientAttachmentStorageUnavailableException;
import backend.preparation.application.PatientSessionUnavailableException;
import jakarta.servlet.http.HttpServletResponse;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = { PatientAccessController.class, PatientAttachmentController.class })
public final class PatientAccessExceptionHandler {

    private final PatientSecurityResponses securityResponses;

    public PatientAccessExceptionHandler(PatientSecurityResponses securityResponses) {
        this.securityResponses = securityResponses;
    }

    @ExceptionHandler(PatientSessionUnavailableException.class)
    public void handleUnavailableSession(HttpServletResponse response) throws IOException {
        securityResponses.unauthorized(response);
    }

    @ExceptionHandler({
            InvalidPatientReasonCommandException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public void handleInvalidPatientRequest(HttpServletResponse response) throws IOException {
        securityResponses.badRequest(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public void handleUnsupportedMediaType(HttpServletResponse response) throws IOException {
        securityResponses.unsupportedMediaType(response);
    }

    @ExceptionHandler(InvalidPatientAttachmentException.class)
    public void handleInvalidAttachment(
            InvalidPatientAttachmentException exception,
            HttpServletResponse response) throws IOException {
        switch (exception.reason()) {
            case BAD_REQUEST -> securityResponses.badRequest(response);
            case TOO_LARGE -> securityResponses.payloadTooLarge(response);
            case UNSUPPORTED_MEDIA_TYPE -> securityResponses.unsupportedMediaType(response);
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void handleMaximumUploadSize(HttpServletResponse response) throws IOException {
        securityResponses.payloadTooLarge(response);
    }

    @ExceptionHandler(MultipartException.class)
    public void handleMalformedMultipart(HttpServletResponse response) throws IOException {
        securityResponses.badRequest(response);
    }

    @ExceptionHandler(PatientAttachmentNotFoundException.class)
    public void handleMissingAttachment(HttpServletResponse response) throws IOException {
        securityResponses.notFound(response);
    }

    @ExceptionHandler(PatientAttachmentStorageUnavailableException.class)
    public void handleAttachmentStorageUnavailable(HttpServletResponse response) throws IOException {
        securityResponses.serviceUnavailable(response);
    }
}
