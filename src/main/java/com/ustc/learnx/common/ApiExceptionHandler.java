package com.ustc.learnx.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps domain exceptions to HTTP responses.
 *
 * <p>Responses carry both the RFC 7807 {@code detail} field and a plain
 * {@code message} property, because the current frontend reads {@code message}.
 * The {@code message} property can go once the React client lands.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** Thrown by {@code @PreAuthorize} when the caller lacks the required role. */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail handleSpringAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(com.ustc.learnx.service.storage.FileStorageService.InvalidUploadException.class)
    public ProblemDetail handleInvalidUpload(
            com.ustc.learnx.service.storage.FileStorageService.InvalidUploadException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(com.ustc.learnx.service.storage.FileStorageService.StorageException.class)
    public ProblemDetail handleStorageFailure(
            com.ustc.learnx.service.storage.FileStorageService.StorageException ex) {
        log.error("File storage failed", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process the file");
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Bean Validation failures, reported per field so a client can point at the
     * input that needs fixing rather than showing a bare "Bad Request".
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (org.springframework.validation.FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        for (org.springframework.validation.ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage());
        }

        String summary = fieldErrors.isEmpty()
                ? "The submitted data is not valid"
                : String.join("; ", fieldErrors.values());

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, summary);
        detail.setProperty("errors", fieldErrors);
        return detail;
    }

    /**
     * A body that could not be parsed at all. Jackson rejects a missing
     * primitive, so this commonly means a form omitted a numeric field; the
     * response says so instead of surfacing the parser's message.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.debug("Rejected an unreadable request body", ex);
        return problem(HttpStatus.BAD_REQUEST,
                "The request body is missing or malformed");
    }

    /** Constraint violations must not surface SQL or column names to callers. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Rejected request that violated a database constraint", ex);
        return problem(HttpStatus.BAD_REQUEST,
                "The submitted data is incomplete or conflicts with an existing record");
    }

    private ProblemDetail problem(HttpStatus status, String message) {
        String safeMessage = (message == null || message.isBlank())
                ? status.getReasonPhrase()
                : message;
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, safeMessage);
        detail.setProperty("message", safeMessage);
        return detail;
    }
}
