package com.selfhealing.github;

import com.selfhealing.exception.GithubApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Low-level GitHub REST API client.
 *
 * Design decisions:
 * - Stateless: PAT is passed per-call, not stored in this bean.
 *   This makes it thread-safe and testable.
 * - All methods take a PAT parameter — the PAT comes from CredentialService,
 *   which decrypts it from MongoDB just-in-time.
 * - Returns Map<String, Object> to avoid defining full GitHub response POJOs
 *   for every endpoint. Callers extract only the fields they need.
 *
 * GitHub API base URL: https://api.github.com
 * API version header: X-GitHub-Api-Version: 2022-11-28
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GithubClient {

    private static final String BASE_URL = "https://api.github.com";
    private static final String API_VERSION = "2022-11-28";

    private final RestTemplate restTemplate;

    // =========================================================
    // AUTHENTICATION
    // =========================================================

    /**
     * Validates a PAT and retrieves the authenticated GitHub user's profile.
     *
     * @param pat GitHub Personal Access Token
     * @return Map with keys: login, id, name, avatar_url, etc.
     * @throws GithubApiException if the token is invalid or has insufficient scopes
     */
    public Map<String, Object> getAuthenticatedUser(String pat) {
        return get("/user", pat);
    }

    // =========================================================
    // REPOSITORIES
    // =========================================================

    /**
     * Fetches repository metadata. Used to validate the user has access.
     *
     * @param owner Repo owner username or org name
     * @param repo  Repository name
     * @param pat   GitHub PAT
     * @return Map with keys: id, full_name, private, default_branch, etc.
     * @throws GithubApiException if repo not found or no access
     */
    public Map<String, Object> getRepository(String owner, String repo, String pat) {
        return get("/repos/" + owner + "/" + repo, pat);
    }

    // =========================================================
    // WORKFLOW RUNS (Phase 4+)
    // =========================================================

    /**
     * Gets a specific workflow run by its ID.
     *
     * @param owner     Repo owner
     * @param repo      Repo name
     * @param runId     GitHub Actions workflow run ID
     * @param pat       GitHub PAT
     * @return Map with keys: id, status, conclusion, head_sha, head_branch, etc.
     */
    public Map<String, Object> getWorkflowRun(String owner, String repo, long runId, String pat) {
        return get("/repos/" + owner + "/" + repo + "/actions/runs/" + runId, pat);
    }

    /**
     * Gets the jobs for a workflow run (contains the individual step results).
     *
     * @return Map with "jobs" array — each job has name, conclusion, steps[]
     */
    public Map<String, Object> getWorkflowRunJobs(String owner, String repo, long runId, String pat) {
        return get("/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs", pat);
    }

    /**
     * Downloads the log archive URL for a workflow run.
     * GitHub returns a redirect URL — the actual logs are at that URL.
     *
     * @return The download URL for the logs zip file
     */
    public String getWorkflowRunLogsUrl(String owner, String repo, long runId, String pat) {
        HttpHeaders headers = buildHeaders(pat);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            // GitHub redirects to S3 — we want the redirect URL, not the body
            ResponseEntity<Void> response = restTemplate.exchange(
                    BASE_URL + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/logs",
                    HttpMethod.GET,
                    entity,
                    Void.class
            );
            // The Location header contains the actual download URL
            String location = response.getHeaders().getFirst("Location");
            if (location == null) {
                throw new GithubApiException("GitHub did not return a log download URL for run " + runId);
            }
            return location;
        } catch (HttpClientErrorException e) {
            throw new GithubApiException("Failed to get log URL for run " + runId + ": " + e.getMessage(), e);
        }
    }

    // =========================================================
    // COMMITS & DIFFS (Phase 5+)
    // =========================================================

    /**
     * Gets a commit and its file diffs.
     *
     * @param sha The commit SHA
     * @return Map with "files" array — each file has filename, patch (diff), status
     */
    public Map<String, Object> getCommit(String owner, String repo, String sha, String pat) {
        return get("/repos/" + owner + "/" + repo + "/commits/" + sha, pat);
    }

    // =========================================================
    // FILE CONTENTS (Phase 5+)
    // =========================================================

    /**
     * Gets the content of a file at a specific commit/branch.
     *
     * @param path  File path relative to repo root (e.g. "src/Main.java")
     * @param ref   Branch name or commit SHA
     * @return Map with "content" (Base64-encoded), "encoding", "sha"
     */
    public Map<String, Object> getFileContent(String owner, String repo, String path, String ref, String pat) {
        return get("/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref, pat);
    }

    // =========================================================
    // GIT OPERATIONS (Phase 8+)
    // =========================================================

    /**
     * Gets the HEAD commit SHA for a branch.
     * Used to create healing branches from the correct base.
     */
    public Map<String, Object> getBranchRef(String owner, String repo, String branch, String pat) {
        return get("/repos/" + owner + "/" + repo + "/git/ref/heads/" + branch, pat);
    }

    /**
     * Creates a new branch from an existing commit SHA.
     * Used to create the healing branch (e.g. "heal/main/run-12345").
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createBranch(String owner, String repo, String branchName, String sha, String pat) {
        Map<String, String> body = Map.of(
                "ref", "refs/heads/" + branchName,
                "sha", sha
        );
        return post("/repos/" + owner + "/" + repo + "/git/refs", body, pat);
    }

    /**
     * Creates or updates a file in a repo (used to apply the AI patch).
     *
     * @param path      File path relative to repo root
     * @param message   Commit message
     * @param content   Base64-encoded new file content
     * @param fileSha   Current file SHA (required by GitHub to prevent conflicts)
     * @param branch    Branch to commit to
     */
    public Map<String, Object> createOrUpdateFile(String owner, String repo, String path,
                                                   String message, String content,
                                                   String fileSha, String branch, String pat) {
        Map<String, Object> body = fileSha != null
                ? Map.of("message", message, "content", content, "sha", fileSha, "branch", branch)
                : Map.of("message", message, "content", content, "branch", branch);

        return put("/repos/" + owner + "/" + repo + "/contents/" + path, body, pat);
    }

    // =========================================================
    // PULL REQUESTS (Phase 9+)
    // =========================================================

    /**
     * Creates a Pull Request on GitHub.
     *
     * @param title     PR title
     * @param body      PR description (AI summary + attempt details)
     * @param head      Source branch (the healing branch)
     * @param base      Target branch (the original failing branch)
     */
    public Map<String, Object> createPullRequest(String owner, String repo, String title,
                                                  String body, String head, String base, String pat) {
        Map<String, Object> requestBody = Map.of(
                "title", title,
                "body", body,
                "head", head,
                "base", base
        );
        return post("/repos/" + owner + "/" + repo + "/pulls", requestBody, pat);
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, String pat) {
        HttpHeaders headers = buildHeaders(pat);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + path,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("GitHub API GET {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GithubApiException("GitHub API error: " + e.getStatusCode() + " on " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String pat) {
        HttpHeaders headers = buildHeaders(pat);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + path, HttpMethod.POST, entity, Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("GitHub API POST {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GithubApiException("GitHub API error: " + e.getStatusCode() + " on " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> put(String path, Object body, String pat) {
        HttpHeaders headers = buildHeaders(pat);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + path, HttpMethod.PUT, entity, Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("GitHub API PUT {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GithubApiException("GitHub API error: " + e.getStatusCode() + " on " + path, e);
        }
    }

    /** Builds headers required for every GitHub API call */
    private HttpHeaders buildHeaders(String pat) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + pat);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", API_VERSION);
        return headers;
    }
}
