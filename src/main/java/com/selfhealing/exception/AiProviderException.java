package com.selfhealing.exception;

/**
 * Thrown when the AI provider (Gemini or Ollama) fails to respond correctly.
 * Examples: rate limit hit, non-JSON response, missing required fields.
 * Maps to HTTP 503 (Service Unavailable) in GlobalExceptionHandler.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
