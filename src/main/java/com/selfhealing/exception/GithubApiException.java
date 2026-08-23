package com.selfhealing.exception;

/**
 * Thrown when a call to the GitHub REST API fails.
 * Wraps the original error message from GitHub's API response.
 * Maps to HTTP 502 (Bad Gateway) in GlobalExceptionHandler.
 */
public class GithubApiException extends RuntimeException {

    public GithubApiException(String message) {
        super(message);
    }

    public GithubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
