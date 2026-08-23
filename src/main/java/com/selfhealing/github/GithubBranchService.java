package com.selfhealing.github;

import com.selfhealing.exception.GithubApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Service to execute Git branch creation and commit operations via GitHub REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GithubBranchService {

    private final GithubClient githubClient;
    private final GithubCommitService commitService;

    /**
     * Creates a new healing branch (e.g. "ai-healing/job-123-attempt-1")
     * pointing to the exact commit SHA that failed CI.
     *
     * @param owner Repo owner
     * @param repo Repo name
     * @param branchName New healing branch name
     * @param baseCommitSha Commit SHA to branch from
     * @param pat Decrypted GitHub PAT
     */
    public void createHealingBranch(String owner, String repo, String branchName, String baseCommitSha, String pat) {
        log.info("Creating healing branch {} on {}/{} from sha {}", branchName, owner, repo, baseCommitSha);
        try {
            githubClient.createBranch(owner, repo, branchName, baseCommitSha, pat);
        } catch (GithubApiException e) {
            log.error("Failed to create healing branch {}: {}", branchName, e.getMessage());
            throw e;
        }
    }

    /**
     * Commits the set of modified files to the specified healing branch.
     *
     * @param owner Repo owner
     * @param repo Repo name
     * @param branchName Healing branch to commit into
     * @param patchedFiles Map of relative filePath -> new file content string
     * @param commitMessage Commit message for the fix
     * @param pat Decrypted GitHub PAT
     */
    public void commitPatchedFiles(
            String owner,
            String repo,
            String branchName,
            Map<String, String> patchedFiles,
            String commitMessage,
            String pat) {

        for (Map.Entry<String, String> entry : patchedFiles.entrySet()) {
            String filePath = entry.getKey();
            String newContent = entry.getValue();

            // Encode new file content to Base64 (required by GitHub API)
            String base64Content = Base64.getEncoder().encodeToString(newContent.getBytes(StandardCharsets.UTF_8));

            // Fetch the existing file SHA on the healing branch to avoid 409 conflict
            String currentFileSha = commitService.getFileSha(owner, repo, filePath, branchName, pat);

            log.info("Committing patched file {} to branch {} (fileSha: {})", filePath, branchName, currentFileSha);
            githubClient.createOrUpdateFile(
                    owner,
                    repo,
                    filePath,
                    commitMessage,
                    base64Content,
                    currentFileSha,
                    branchName,
                    pat
            );
        }
    }

    /**
     * Gets the latest commit SHA for a branch.
     * Used to verify that the original branch has not changed while healing was in progress.
     */
    @SuppressWarnings("unchecked")
    public String getBranchHeadSha(String owner, String repo, String branch, String pat) {
        try {
            Map<String, Object> refData = githubClient.getBranchRef(owner, repo, branch, pat);
            Map<String, Object> objectData = (Map<String, Object>) refData.get("object");
            return (String) objectData.get("sha");
        } catch (Exception e) {
            log.warn("Failed to get HEAD sha for branch {}: {}", branch, e.getMessage());
            return null;
        }
    }
}
