package com.selfhealing.github;

import com.selfhealing.exception.GithubApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to fetch commit diffs and source file contents from GitHub at exact commit SHAs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GithubCommitService {

    private final GithubClient githubClient;

    /**
     * Retrieves the git diff and list of changed files for a commit.
     *
     * @param owner Repo owner
     * @param repo Repo name
     * @param sha Commit SHA
     * @param pat GitHub PAT
     * @return Map with "diffText" (combined patch) and "changedFiles" (list of filenames)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCommitDiff(String owner, String repo, String sha, String pat) {
        try {
            Map<String, Object> commitData = githubClient.getCommit(owner, repo, sha, pat);
            List<Map<String, Object>> files = (List<Map<String, Object>>) commitData.get("files");

            StringBuilder diffBuilder = new StringBuilder();
            List<String> changedFiles = new ArrayList<>();

            if (files != null) {
                for (Map<String, Object> file : files) {
                    String filename = (String) file.get("filename");
                    String patch = (String) file.get("patch");
                    changedFiles.add(filename);

                    diffBuilder.append("diff --git a/").append(filename).append(" b/").append(filename).append("\n");
                    if (patch != null) {
                        diffBuilder.append(patch).append("\n\n");
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("diffText", diffBuilder.toString());
            result.put("changedFiles", changedFiles);
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch commit diff for sha {}: {}", sha, e.getMessage());
            return Map.of("diffText", "No diff available: " + e.getMessage(), "changedFiles", List.of());
        }
    }

    /**
     * Fetches and decodes the full text content of a file at a specific commit SHA.
     *
     * @param owner Repo owner
     * @param repo Repo name
     * @param path File path relative to repo root
     * @param sha Exact commit SHA (never HEAD)
     * @param pat GitHub PAT
     * @return Decoded plain-text file content
     */
    public String getFileContent(String owner, String repo, String path, String sha, String pat) {
        try {
            Map<String, Object> response = githubClient.getFileContent(owner, repo, path, sha, pat);
            String contentBase64 = (String) response.get("content");
            String encoding = (String) response.get("encoding");

            if (contentBase64 == null) {
                return null;
            }

            // GitHub returns multiline base64 containing newlines
            if ("base64".equalsIgnoreCase(encoding)) {
                String cleanBase64 = contentBase64.replaceAll("\\s+", "");
                byte[] decoded = Base64.getDecoder().decode(cleanBase64);
                return new String(decoded, StandardCharsets.UTF_8);
            }

            return contentBase64;
        } catch (GithubApiException e) {
            log.warn("File {} not found at sha {}: {}", path, sha, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to decode file {} at sha {}: {}", path, sha, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the SHA of a specific file at a branch/commit (needed by GitHub API to update files).
     */
    public String getFileSha(String owner, String repo, String path, String ref, String pat) {
        try {
            Map<String, Object> response = githubClient.getFileContent(owner, repo, path, ref, pat);
            return (String) response.get("sha");
        } catch (Exception e) {
            return null;
        }
    }
}
