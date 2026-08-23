package com.selfhealing.controller;

import com.selfhealing.dto.request.ConnectGithubRequest;
import com.selfhealing.dto.response.ApiResponse;
import com.selfhealing.service.CredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GitHub credential management controller.
 *
 * POST /api/github/connect — store/update the user's GitHub PAT
 * GET  /api/github/status  — check if GitHub is connected
 *
 * authentication.getPrincipal() returns the userId stored in the JWT subject claim.
 * This is set by JwtAuthFilter.
 */
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final CredentialService credentialService;

    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<Map<String, String>>> connectGithub(
            @Valid @RequestBody ConnectGithubRequest request,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        // The raw PAT is validated against GitHub then encrypted — never returned
        String githubUsername = credentialService.saveToken(userId, request.getToken());

        return ResponseEntity.ok(ApiResponse.success(
                "GitHub account connected successfully",
                Map.of("githubUsername", githubUsername)
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        boolean connected = credentialService.hasCredential(userId);

        Map<String, Object> status = connected
                ? Map.of("connected", true,  "githubUsername", credentialService.getGithubUsername(userId))
                : Map.of("connected", false);

        return ResponseEntity.ok(ApiResponse.success("GitHub connection status", status));
    }
}
