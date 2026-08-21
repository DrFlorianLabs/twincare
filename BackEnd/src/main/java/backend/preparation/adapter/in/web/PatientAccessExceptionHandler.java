package backend.preparation.adapter.in.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import backend.preparation.application.InvalidPatientReasonCommandException;
import backend.preparation.application.PatientSessionUnavailableException;
import jakarta.servlet.http.HttpServletResponse;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PatientAccessController.class)
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
}
