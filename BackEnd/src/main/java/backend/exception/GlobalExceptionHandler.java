package backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import backend.clinicalcontext.application.ClinicalContextNotFoundException;
import backend.clinicalcontext.application.ClinicalFactConflictException;
import backend.clinicalcontext.application.ClinicalFactNotFoundException;
import backend.dto.ApiError;
import backend.preparation.application.InvalidPreparationCaseCommandException;
import backend.preparation.application.PatientInvitationConflictException;
import backend.preparation.application.PatientInvitationUnavailableException;
import backend.preparation.application.PreparationCaseConflictException;
import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.PreparationAttachmentNotFoundException;
import backend.preparation.application.ProfessionalAttachmentStorageUnavailableException;
import backend.preparation.application.PreparationTaskNotFoundException;
import backend.preparation.application.PreparationWorkflowConflictException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<ApiError> handleForbidden(UnauthorizedActionException e) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + " : " + error.getDefaultMessage())
                .orElse("Requête invalide");

        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Requête invalide");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleOversizedUpload(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Fichier trop volumineux");
    }

    @ExceptionHandler(InvalidPreparationCaseCommandException.class)
    public ResponseEntity<ApiError> handleInvalidPreparationCase(InvalidPreparationCaseCommandException e) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PREPARATION_CASE",
                "Requête de création de préparation invalide");
    }

    @ExceptionHandler(PreparationCaseConflictException.class)
    public ResponseEntity<ApiError> handlePreparationCaseConflict(PreparationCaseConflictException e) {
        return error(
                HttpStatus.CONFLICT,
                "PREPARATION_CASE_CONFLICT",
                "Une préparation existe déjà pour cette consultation");
    }

    @ExceptionHandler(PreparationCaseNotFoundException.class)
    public ResponseEntity<String> handlePreparationCaseNotFound(PreparationCaseNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Préparation introuvable");
    }

    @ExceptionHandler(PreparationAttachmentNotFoundException.class)
    public ResponseEntity<String> handlePreparationAttachmentNotFound(PreparationAttachmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Document de préparation introuvable");
    }

    @ExceptionHandler(ProfessionalAttachmentStorageUnavailableException.class)
    public ResponseEntity<String> handlePreparationAttachmentStorageUnavailable(
            ProfessionalAttachmentStorageUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Document de préparation temporairement indisponible");
    }

    @ExceptionHandler(PreparationTaskNotFoundException.class)
    public ResponseEntity<String> handlePreparationTaskNotFound(PreparationTaskNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Tâche de préparation introuvable");
    }

    @ExceptionHandler(PreparationWorkflowConflictException.class)
    public ResponseEntity<String> handlePreparationWorkflowConflict(PreparationWorkflowConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("La préparation a changé ou cette action n'est plus disponible");
    }

    @ExceptionHandler(PatientInvitationConflictException.class)
    public ResponseEntity<ApiError> handlePatientInvitationConflict(PatientInvitationConflictException e) {
        return error(
                HttpStatus.CONFLICT,
                "PATIENT_INVITATION_CONFLICT",
                "L'invitation patient n'est pas disponible dans l'état actuel");
    }

    @ExceptionHandler(PatientInvitationUnavailableException.class)
    public ResponseEntity<String> handlePatientInvitationUnavailable(PatientInvitationUnavailableException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invitation patient indisponible");
    }

    @ExceptionHandler(ClinicalContextNotFoundException.class)
    public ResponseEntity<ApiError> handleClinicalContextNotFound(ClinicalContextNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "CLINICAL_CONTEXT_NOT_FOUND", "Contexte clinique introuvable");
    }

    @ExceptionHandler(ClinicalFactNotFoundException.class)
    public ResponseEntity<ApiError> handleClinicalFactNotFound(ClinicalFactNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "CLINICAL_FACT_NOT_FOUND", "Fait clinique introuvable");
    }

    @ExceptionHandler(ClinicalFactConflictException.class)
    public ResponseEntity<ApiError> handleClinicalFactConflict(ClinicalFactConflictException e) {
        return error(
                HttpStatus.CONFLICT,
                "CLINICAL_FACT_CONFLICT",
                "Le fait clinique ne peut pas être modifié dans son état actuel");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception e) {
        log.error("Unhandled application error type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Erreur interne du serveur");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
