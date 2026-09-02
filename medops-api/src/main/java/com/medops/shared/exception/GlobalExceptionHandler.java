package com.medops.shared.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.medops.auth.exception.InvalidRefreshTokenException;
import com.medops.shared.response.ErrorDetail;
import com.medops.shared.response.ErrorResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler that converts application exceptions into standardized
 * error responses following Google's API error format.
 * <p>
 * Handles validation errors, HTTP errors, and unexpected exceptions with
 * appropriate status codes and error details.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";

    /**
     * Handles request body validation failures from {@code @Valid} annotations.
     *
     * @param ex the validation exception containing field errors
     * @return 400 Bad Request with detailed validation messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, INVALID_ARGUMENT, "Validation failed", details);
    }

    /**
     * Handles path/query parameter validation failures from {@code @Validated}.
     *
     * @param ex the constraint violation exception
     * @return 400 Bad Request with constraint violation details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, INVALID_ARGUMENT, "Validation failed", details);
    }

    /**
     * Handles requests to non-existent endpoints.
     *
     * @param ex the no resource found exception
     * @return 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    /**
     * Handles requests with unsupported HTTP methods.
     *
     * @param ex the method not supported exception
     * @return 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), null);
    }

    /**
     * Handles authentication failures (bad credentials, missing user).
     *
     * @param ex the authentication exception
     * @return 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid email or password", null);
    }

    /**
     * Handles login lockout after too many failed passwords for the same email.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException ex) {
        return build(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "Too many failed sign-in attempts. Try again later.", null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        return build(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "Account is disabled", null);
    }

    /**
     * Handles access to resources the authenticated user is not authorized for.
     *
     * @param ex the access denied exception
     * @return 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(
                HttpStatus.FORBIDDEN,
                "PERMISSION_DENIED",
                "You do not have permission to perform this action",
                null);
    }

    /**
     * Handles refresh token validation failures (missing, expired, or revoked).
     *
     * @param ex the invalid refresh token exception
     * @return 401 Unauthorized
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", ex.getMessage(), null);
    }

    /**
     * Handles attempts to create a resource that would violate a uniqueness constraint
     * the client controls (e.g. a duplicate email or license number at registration).
     *
     * @param ex the conflict exception
     * @return 409 Conflict
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "ALREADY_EXISTS", ex.getMessage(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, INVALID_ARGUMENT, ex.getMessage(), null);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "RESOURCE_EXHAUSTED", ex.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.BAD_REQUEST, INVALID_ARGUMENT, "PDF files must be 10 MB or smaller", null);
    }

    /**
     * Maps uniqueness races (for example two patients booking the same doctor slot)
     * onto the same 409 the use case throws after a pre-check.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "ALREADY_EXISTS", "That time is no longer available", null);
    }

    /**
     * Handles all unhandled exceptions to prevent stack traces from leaking to clients.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "An unexpected error occurred", null);
    }

    /**
     * Builds a standardized error response.
     *
     * @param status HTTP status
     * @param googleStatus semantic status following Google's conventions
     * @param message human-readable error message
     * @param details specific error details (e.g., validation failures)
     * @return formatted error response
     */
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String googleStatus, String message, List<String> details) {
        return ResponseEntity.status(status.value())
                .body(ErrorResponse.of(ErrorDetail.of(status.value(), googleStatus, message, details)));
    }
}
