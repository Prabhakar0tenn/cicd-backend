package com.selfhealing.service;

import com.selfhealing.enums.CredentialStatus;
import com.selfhealing.exception.GithubApiException;
import com.selfhealing.exception.ResourceNotFoundException;
import com.selfhealing.github.GithubClient;
import com.selfhealing.model.GithubCredential;
import com.selfhealing.repository.GithubCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Manages GitHub Personal Access Tokens.
 *
 * Responsibilities:
 * 1. Validate the PAT with GitHub's API before storing
 * 2. Encrypt the PAT with AES-256-GCM before persisting
 * 3. Decrypt the PAT ONLY when needed for GitHub API calls
 * 4. Never return or log the plain-text PAT
 *
 * This service is the ONLY class that holds plain-text PATs in memory,
 * and only briefly during validate → encrypt → discard cycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialService {

    private final GithubCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final GithubClient githubClient;

    /**
     * Saves (or updates) a GitHub PAT for the given user.
     *
     * Steps:
     * 1. Call GitHub /user with the PAT to validate it and get the username
     * 2. Encrypt the PAT with AES-256-GCM
     * 3. Save the encrypted token to MongoDB
     * 4. Return the GitHub username (not the token)
     *
     * @param userId   The platform user's MongoDB ID
     * @param plainPat The raw PAT from the request (discarded after this method)
     * @return GitHub username (e.g. "prabhakar")
     * @throws GithubApiException if the PAT is invalid or lacks required scopes
     */
    public String saveToken(String userId, String plainPat) {
        // Step 1: Validate the token FIRST — fail fast before touching the database
        log.info("Validating GitHub PAT for user {}", userId);
        Map<String, Object> githubUser = githubClient.getAuthenticatedUser(plainPat);
        String githubUsername = (String) githubUser.get("login");

        if (githubUsername == null || githubUsername.isBlank()) {
            throw new GithubApiException("GitHub API returned no username — token may lack read:user scope");
        }

        // Step 2: Encrypt (plain PAT leaves memory scope at end of this block)
        String encryptedToken = encryptionService.encrypt(plainPat);

        // Step 3: Upsert — update if exists, insert if new
        GithubCredential credential = credentialRepository.findByUserId(userId)
                .orElse(GithubCredential.builder().userId(userId).build());

        credential.setEncryptedToken(encryptedToken);
        credential.setGithubUsername(githubUsername);
        credential.setLastValidatedAt(Instant.now());
        credential.setStatus(CredentialStatus.ACTIVE);

        credentialRepository.save(credential);

        log.info("GitHub token saved for user {} (GitHub: {})", userId, githubUsername);
        return githubUsername;
    }

    /**
     * Decrypts and returns the plain-text PAT for the given user.
     * Called by other services (RepositoryService, HealingService) when they
     * need to make GitHub API calls on behalf of the user.
     *
     * IMPORTANT: The returned String is a plain-text PAT.
     * The caller must NOT log it, return it to the client, or persist it.
     *
     * @param userId The platform user's MongoDB ID
     * @return The decrypted plain-text GitHub PAT
     * @throws ResourceNotFoundException if the user has not connected GitHub
     */
    public String getDecryptedToken(String userId) {
        GithubCredential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("GithubCredential", userId));

        return encryptionService.decrypt(credential.getEncryptedToken());
    }

    /**
     * Returns the GitHub username for the given user without exposing the token.
     *
     * @throws ResourceNotFoundException if the user has not connected GitHub
     */
    public String getGithubUsername(String userId) {
        return credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("GithubCredential", userId))
                .getGithubUsername();
    }

    /** Returns true if the user has a stored GitHub credential */
    public boolean hasCredential(String userId) {
        return credentialRepository.existsByUserId(userId);
    }
}
