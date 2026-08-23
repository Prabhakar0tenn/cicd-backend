package com.selfhealing.controller;

import com.selfhealing.dto.request.AddRepositoryRequest;
import com.selfhealing.dto.request.BranchConfigRequest;
import com.selfhealing.dto.response.ApiResponse;
import com.selfhealing.dto.response.RepoResponse;
import com.selfhealing.model.MonitoredRepo;
import com.selfhealing.service.RepositoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing monitored GitHub repositories.
 *
 * All endpoints require a valid JWT (Spring Security enforces this).
 * The authenticated user's ID is extracted from Authentication.getPrincipal().
 *
 * Endpoints:
 * POST   /api/repositories           — connect a new repo
 * GET    /api/repositories           — list all connected repos
 * GET    /api/repositories/{id}      — get a specific repo
 * POST   /api/repositories/{id}/branches   — add/update branch config
 * PATCH  /api/repositories/{id}/healing    — toggle auto-heal on/off
 * DELETE /api/repositories/{id}      — disconnect repo
 */
@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<RepoResponse>> addRepository(
            @Valid @RequestBody AddRepositoryRequest request,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        MonitoredRepo repo = repositoryService.addRepository(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Repository connected successfully", RepoResponse.from(repo)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepoResponse>>> getRepositories(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        List<RepoResponse> repos = repositoryService.getRepositories(userId)
                .stream()
                .map(RepoResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Repositories fetched", repos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RepoResponse>> getRepository(
            @PathVariable String id,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        MonitoredRepo repo = repositoryService.getRepositoryById(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Repository fetched", RepoResponse.from(repo)));
    }

    @PostMapping("/{id}/branches")
    public ResponseEntity<ApiResponse<RepoResponse>> configureBranch(
            @PathVariable String id,
            @Valid @RequestBody BranchConfigRequest request,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        MonitoredRepo repo = repositoryService.configureBranch(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Branch configured", RepoResponse.from(repo)));
    }

    @PatchMapping("/{id}/healing")
    public ResponseEntity<ApiResponse<RepoResponse>> toggleHealing(
            @PathVariable String id,
            @RequestParam boolean enabled,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        MonitoredRepo repo = repositoryService.setAutoHeal(userId, id, enabled);
        return ResponseEntity.ok(ApiResponse.success(
                "Auto-heal " + (enabled ? "enabled" : "disabled"),
                RepoResponse.from(repo)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteRepository(
            @PathVariable String id,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        repositoryService.deleteRepository(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Repository disconnected", Map.of("id", id)));
    }
}
