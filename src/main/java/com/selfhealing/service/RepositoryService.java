package com.selfhealing.service;

import com.selfhealing.dto.request.AddRepositoryRequest;
import com.selfhealing.dto.request.BranchConfigRequest;
import com.selfhealing.exception.GithubApiException;
import com.selfhealing.exception.ResourceNotFoundException;
import com.selfhealing.github.GithubClient;
import com.selfhealing.model.BranchConfig;
import com.selfhealing.model.MonitoredRepo;
import com.selfhealing.repository.MonitoredRepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Business logic for managing monitored repositories.
 *
 * Workflow for adding a repo:
 * 1. Ensure the user has a GitHub PAT stored (credential check)
 * 2. Validate the repo exists and the user has access (via GitHub API)
 * 3. Check the repo isn't already connected
 * 4. Save to MongoDB with a default "main" branch config
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryService {

    private final MonitoredRepoRepository repoRepository;
    private final CredentialService credentialService;
    private final GithubClient githubClient;

    /**
     * Connects a new GitHub repository to the platform.
     *
     * @param userId  The authenticated user's platform ID
     * @param request Contains owner and repository name
     * @return The saved MonitoredRepo document
     */
    public MonitoredRepo addRepository(String userId, AddRepositoryRequest request) {
        String owner = request.getOwner().trim();
        String name = request.getName().trim();

        // Guard: user must have a GitHub credential
        if (!credentialService.hasCredential(userId)) {
            throw new GithubApiException("Please connect your GitHub account first (POST /api/github/connect)");
        }

        // Guard: no duplicate repos per user
        if (repoRepository.existsByUserIdAndOwnerAndName(userId, owner, name)) {
            throw new IllegalArgumentException("Repository " + owner + "/" + name + " is already connected");
        }

        // Validate access via GitHub API
        String pat = credentialService.getDecryptedToken(userId);
        Map<String, Object> githubRepo = validateGithubRepoAccess(owner, name, pat);

        // Get the default branch from GitHub (usually "main" or "master")
        String defaultBranch = (String) githubRepo.getOrDefault("default_branch", "main");

        // Build the document with a default branch config for the repo's default branch
        MonitoredRepo repo = MonitoredRepo.builder()
                .userId(userId)
                .owner(owner)
                .name(name)
                .autoHealEnabled(true)
                .branches(List.of(BranchConfig.builder()
                        .name(defaultBranch)
                        .healingEnabled(true)
                        .maxAttempts(3)
                        .build()))
                .build();

        MonitoredRepo saved = repoRepository.save(repo);
        log.info("Repository {}/{} connected for user {}", owner, name, userId);
        return saved;
    }

    /**
     * Returns all repos connected by the given user.
     */
    public List<MonitoredRepo> getRepositories(String userId) {
        return repoRepository.findByUserId(userId);
    }

    /**
     * Gets a specific repo — verifies ownership (userId + repoId must match).
     *
     * @throws ResourceNotFoundException if repo not found or owned by someone else
     */
    public MonitoredRepo getRepositoryById(String userId, String repoId) {
        MonitoredRepo repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repoId));

        if (!repo.getUserId().equals(userId)) {
            // Return "not found" instead of "forbidden" — avoids leaking repo IDs
            throw new ResourceNotFoundException("Repository", repoId);
        }

        return repo;
    }

    /**
     * Adds or updates a branch configuration within a repository.
     * If the branch config already exists, updates it. Otherwise, adds a new entry.
     */
    public MonitoredRepo configureBranch(String userId, String repoId, BranchConfigRequest request) {
        MonitoredRepo repo = getRepositoryById(userId, repoId);

        List<BranchConfig> branches = repo.getBranches();

        // Remove existing config for this branch (if any), then add the updated one
        branches.removeIf(b -> b.getName().equals(request.getBranchName()));
        branches.add(BranchConfig.builder()
                .name(request.getBranchName())
                .healingEnabled(request.isHealingEnabled())
                .maxAttempts(request.getMaxAttempts())
                .build());

        return repoRepository.save(repo);
    }

    /**
     * Toggles auto-heal for an entire repository.
     */
    public MonitoredRepo setAutoHeal(String userId, String repoId, boolean autoHealEnabled) {
        MonitoredRepo repo = getRepositoryById(userId, repoId);
        repo.setAutoHealEnabled(autoHealEnabled);
        return repoRepository.save(repo);
    }

    /**
     * Removes a connected repository. Stops future monitoring.
     * Does NOT delete existing HealingJobs for auditability.
     */
    public void deleteRepository(String userId, String repoId) {
        MonitoredRepo repo = getRepositoryById(userId, repoId);
        repoRepository.delete(repo);
        log.info("Repository {} disconnected by user {}", repoId, userId);
    }

    /**
     * Finds a MonitoredRepo by owner+name+userId.
     * Used by HealingService when a webhook arrives to find the correct repo config.
     *
     * @throws ResourceNotFoundException if not found
     */
    public MonitoredRepo findByOwnerAndName(String userId, String owner, String name) {
        return repoRepository.findByUserIdAndOwnerAndName(userId, owner, name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Repository", owner + "/" + name + " (user: " + userId + ")"));
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    /**
     * Calls GitHub API to verify the repo exists and the PAT has access.
     * Throws GithubApiException (→ 502) if the repo is unreachable.
     */
    private Map<String, Object> validateGithubRepoAccess(String owner, String name, String pat) {
        try {
            Map<String, Object> repo = githubClient.getRepository(owner, name, pat);
            log.debug("GitHub repo {}/{} validated — private: {}, default branch: {}",
                    owner, name, repo.get("private"), repo.get("default_branch"));
            return repo;
        } catch (GithubApiException e) {
            throw new GithubApiException(
                    "Cannot access " + owner + "/" + name + ". Make sure the repo exists and your PAT has 'repo' scope.", e);
        }
    }
}
