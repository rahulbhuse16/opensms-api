package com.opensms.common.exception;

import com.opensms.common.dto.ApiErrorResponse;
import com.opensms.contact.exception.DuplicateContactPhoneException;
import com.opensms.message.idempotency.IdempotencyKeyConflictException;
import com.opensms.webhook.exception.WebhookEncryptionConfigurationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoSuchElementException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRoute(HttpServletRequest request) {
        return createResponse(HttpStatus.NOT_FOUND, "The requested resource was not found.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed.";
        }
        return createResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid value for request parameter or path variable '" + exception.getName() + "'.",
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpServletRequest request) {
        return createResponse(HttpStatus.BAD_REQUEST, "Request body contains invalid or unsupported values.", request);
    }

    @ExceptionHandler(WebhookEncryptionConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleWebhookEncryptionConfiguration(
            WebhookEncryptionConfigurationException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Webhook signing encryption is not configured correctly.",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        String message = exception.getMessage() == null ? "Invalid request." : exception.getMessage();
        return createResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DuplicateContactPhoneException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateContactPhone(
            DuplicateContactPhoneException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyKeyConflict(
            IdempotencyKeyConflictException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            IllegalStateException exception,
            HttpServletRequest request
    ) {
        String message = exception.getMessage() == null ? "The requested operation conflicts with the current state."
                : exception.getMessage();
        return createResponse(HttpStatus.CONFLICT, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(HttpServletRequest request) {
        return createResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> createResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                Instant.now()
        );
        return ResponseEntity.status(status).body(response);
    }
}
