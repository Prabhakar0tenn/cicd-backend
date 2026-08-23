package com.selfhealing.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * Standard API response wrapper used by ALL endpoints in this application.
 *
 * Every response follows the same shape:
 * {
 *   "success": true/false,
 *   "message": "Human-readable status message",
 *   "data": { ... }   // null on error responses
 * }
 *
 * This consistency makes the frontend's job much easier —
 * it always knows where to find the data and how to detect errors.
 *
 * @param <T> The type of the data payload
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include "data: null" in error responses
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    // Private constructor — use factory methods below for clarity
    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a success response with data payload.
     * Example: ApiResponse.success("Repository connected", repoResponse)
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * Creates a success response with no data (e.g., for DELETE operations).
     * Example: ApiResponse.success("Repository removed")
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /**
     * Creates an error response.
     * Example: ApiResponse.error("Repository not found")
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
