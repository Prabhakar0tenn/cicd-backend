package com.selfhealing.exception;

import com.selfhealing.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralized exception handler for the entire application.
 *
 * Instead of try/catch blocks scattered across controllers and services,
 * all exceptions bubble up here and get converted into consistent
 * ApiResponse error objects with the right HTTP status codes.
 *
 * This is the DRY principle applied to error handling.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles validation errors from @Valid on request DTOs.
     * Returns 400 with a list of all validation failures so the frontend
     * can show field-level error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException exception) {

        String errorMessage = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed: " + errorMessage));
    }

    /**
     * Handles wrong username or password during login.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException exception) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid username or password"));
    }

    /**
     * Handles "entity not found" cases — repository not found, job not found, etc.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(exception.getMessage()));
    }

    /**
     * Handles GitHub API call failures.
     */
    @ExceptionHandler(GithubApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleGithubApiError(
            GithubApiException exception) {

        log.error("GitHub API error: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("GitHub API error: " + exception.getMessage()));
    }

    /**
     * Handles cases where the AI's proposed patch fails safety validation.
     */
    @ExceptionHandler(PatchValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePatchValidationError(
            PatchValidationException exception) {

        log.warn("Patch rejected by safety validator: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error("Patch validation failed: " + exception.getMessage()));
    }

    /**
     * Handles AI provider failures (Gemini rate limits, parse errors, etc.)
     */
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProviderError(
            AiProviderException exception) {

        log.error("AI provider error: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("AI service error: " + exception.getMessage()));
    }

    /**
     * Catch-all for any unexpected exceptions.
     * Logs the full stack trace but returns a generic message to the client
     * — never leak internal details to the frontend.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericError(Exception exception) {
        log.error("Unexpected error occurred", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again."));
    }
}
