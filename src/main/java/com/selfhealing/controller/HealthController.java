package com.selfhealing.controller;

import com.selfhealing.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check endpoint for monitoring and deployment readiness probes.
 *
 * Render.com and other hosting platforms ping /api/health to verify
 * the service is running. This endpoint is public (no JWT required).
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Returns a simple OK response with a server timestamp.
     * Used by:
     * - Render.com deployment health checks
     * - Frontend startup verification
     * - Manual smoke testing
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> healthCheck() {
        Map<String, String> healthData = Map.of(
                "status", "ok",
                "service", "Self-Healing CI/CD Backend",
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.ok(ApiResponse.success("Service is running", healthData));
    }
}
