package backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import backend.preparation.application.InvalidPreparationCaseCommandException;
import backend.preparation.application.PatientInvitationConflictException;
import backend.preparation.application.PatientInvitationUnavailableException;
import backend.preparation.application.PreparationCaseConflictException;
import backend.preparation.application.PreparationCaseNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<String> handleBadRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<String> handleForbidden(UnauthorizedActionException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + " : " + error.getDefaultMessage())
                .orElse("Requête invalide");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<String> handleMalformedRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Requête invalide");
    }

    @ExceptionHandler(InvalidPreparationCaseCommandException.class)
    public ResponseEntity<String> handleInvalidPreparationCase(InvalidPreparationCaseCommandException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Requête de création de préparation invalide");
    }

    @ExceptionHandler(PreparationCaseConflictException.class)
    public ResponseEntity<String> handleConflict(PreparationCaseConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Une préparation existe déjà pour cette consultation");
    }

    @ExceptionHandler(PreparationCaseNotFoundException.class)
    public ResponseEntity<String> handlePreparationCaseNotFound(PreparationCaseNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Préparation introuvable");
    }

    @ExceptionHandler(PatientInvitationConflictException.class)
    public ResponseEntity<String> handlePatientInvitationConflict(PatientInvitationConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("L'invitation patient n'est pas disponible dans l'état actuel");
    }

    @ExceptionHandler(PatientInvitationUnavailableException.class)
    public ResponseEntity<String> handlePatientInvitationUnavailable(PatientInvitationUnavailableException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invitation patient indisponible");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception e) {
        log.error("Unhandled application error type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Erreur interne du serveur");
    }
}
