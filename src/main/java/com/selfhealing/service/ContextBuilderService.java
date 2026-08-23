package com.selfhealing.service;

import com.selfhealing.domain.FailureContext;
import com.selfhealing.domain.FailureInfo;
import com.selfhealing.github.GithubCommitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the FailureContext containing git diff, failure diagnosis,
 * and relevant source files at the exact failed commit SHA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextBuilderService {

    private final GithubCommitService commitService;

    // Safety limits for AI prompt size
    private static final int MAX_SOURCE_FILES = 6;
    private static final int MAX_FILE_CHARS = 40_000;

    /**
     * Builds a comprehensive FailureContext for AI reasoning.
     *
     * @param owner Repo owner
     * @param repo Repo name
     * @param branch Monitored branch
     * @param failedCommitSha The exact commit that failed CI
     * @param workflowRunId GitHub Actions run ID
     * @param failureInfo Parsed failure info from logs
     * @param previousAttempts Feedback from prior attempts (for retries)
     * @param pat Decrypted GitHub PAT
     * @return Assembled FailureContext ready for Gemini
     */
    @SuppressWarnings("unchecked")
    public FailureContext buildContext(
            String owner,
            String repo,
            String branch,
            String failedCommitSha,
            long workflowRunId,
            FailureInfo failureInfo,
            List<String> previousAttempts,
            String pat) {

        log.info("Building failure context for {}/{} at commit {}", owner, repo, failedCommitSha);

        // 1. Fetch commit diff
        Map<String, Object> diffData = commitService.getCommitDiff(owner, repo, failedCommitSha, pat);
        String diffText = (String) diffData.getOrDefault("diffText", "");
        List<String> changedFiles = (List<String>) diffData.getOrDefault("changedFiles", List.of());

        // 2. Identify all candidate files to fetch (changed files + error location files)
        Set<String> candidateFiles = new HashSet<>();
        candidateFiles.addAll(changedFiles);
        if (failureInfo.getErrorLocations() != null) {
            candidateFiles.addAll(failureInfo.getErrorLocations());
        }

        // 3. Fetch exact file contents at failedCommitSha
        Map<String, String> sourceFiles = new HashMap<>();
        int count = 0;

        for (String filePath : candidateFiles) {
            if (count >= MAX_SOURCE_FILES) {
                break;
            }

            // Filter out lockfiles, binaries, images, or huge files
            if (isAnalyzableSourceFile(filePath)) {
                String content = commitService.getFileContent(owner, repo, filePath, failedCommitSha, pat);
                if (content != null && !content.isBlank()) {
                    if (content.length() > MAX_FILE_CHARS) {
                        content = content.substring(0, MAX_FILE_CHARS) + "\n\n// ... [TRUNCATED - File exceeded size limit]";
                    }
                    sourceFiles.put(filePath, content);
                    count++;
                }
            }
        }

        return FailureContext.builder()
                .owner(owner)
                .repoName(repo)
                .branch(branch)
                .failedCommitSha(failedCommitSha)
                .workflowRunId(workflowRunId)
                .failureInfo(failureInfo)
                .commitDiff(diffText)
                .sourceFiles(sourceFiles)
                .previousAttemptFeedback(previousAttempts != null ? previousAttempts : List.of())
                .build();
    }

    /**
     * Checks if a file path is a valid text source code file suitable for analysis.
     */
    private boolean isAnalyzableSourceFile(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".lock") || lower.endsWith("-lock.json") || lower.endsWith(".jar") ||
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".svg") ||
            lower.endsWith(".min.js") || lower.endsWith(".map") || lower.endsWith(".class")) {
            return false;
        }
        return true;
    }
}
