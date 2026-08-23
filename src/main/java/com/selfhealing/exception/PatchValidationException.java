package com.selfhealing.exception;

/**
 * Thrown when the AI's proposed patch fails one or more safety checks.
 * Example: file path contains "../", old code not found in file, etc.
 * Maps to HTTP 422 (Unprocessable Entity) in GlobalExceptionHandler.
 */
public class PatchValidationException extends RuntimeException {

    public PatchValidationException(String reason) {
        super(reason);
    }
}
