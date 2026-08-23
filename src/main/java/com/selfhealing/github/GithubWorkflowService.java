package com.selfhealing.github;

import com.selfhealing.exception.GithubApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service to interact with GitHub Actions workflows, jobs, and build logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GithubWorkflowService {

    private final GithubClient githubClient;
    private final RestTemplate restTemplate;

    /**
     * Finds the failed job and failed step from a workflow run.
     *
     * @param owner Repo owner
     * @param repo Repo name
     * @param runId Workflow run ID
     * @param pat Decrypted PAT
     * @return Map containing failed job information (jobName, failedStepName)
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getFailedJobInfo(String owner, String repo, long runId, String pat) {
        try {
            Map<String, Object> jobsResponse = githubClient.getWorkflowRunJobs(owner, repo, runId, pat);
            List<Map<String, Object>> jobs = (List<Map<String, Object>>) jobsResponse.get("jobs");

            if (jobs == null || jobs.isEmpty()) {
                return Map.of("jobName", "unknown", "failedStep", "unknown");
            }

            for (Map<String, Object> job : jobs) {
                String conclusion = (String) job.get("conclusion");
                if ("failure".equalsIgnoreCase(conclusion)) {
                    String jobName = (String) job.get("name");
                    List<Map<String, Object>> steps = (List<Map<String, Object>>) job.get("steps");
                    String failedStep = "build";

                    if (steps != null) {
                        for (Map<String, Object> step : steps) {
                            if ("failure".equalsIgnoreCase((String) step.get("conclusion"))) {
                                failedStep = (String) step.get("name");
                                break;
                            }
                        }
                    }
                    return Map.of("jobName", jobName != null ? jobName : "build", "failedStep", failedStep);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve failed job metadata for run {}: {}", runId, e.getMessage());
        }

        return Map.of("jobName", "build", "failedStep", "build");
    }

    /**
     * Downloads and extracts the log text from a GitHub Actions workflow run.
     * GitHub logs come as a ZIP archive containing individual step log files.
     *
     * @param owner Repo owner
     * @param repo Repo name
     * @param runId Workflow run ID
     * @param pat Decrypted PAT
     * @return Combined log text of failing steps (capped to a safe size)
     */
    public String downloadWorkflowLogs(String owner, String repo, long runId, String pat) {
        try {
            String logUrl = githubClient.getWorkflowRunLogsUrl(owner, repo, runId, pat);
            log.info("Downloading workflow logs from URL: {}", logUrl.substring(0, Math.min(60, logUrl.length())) + "...");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + pat);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    logUrl,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            byte[] zipData = response.getBody();
            if (zipData == null || zipData.length == 0) {
                return "No logs returned from GitHub for run " + runId;
            }

            return extractLogsFromZip(zipData);
        } catch (Exception e) {
            log.error("Error downloading logs for run {}: {}", runId, e.getMessage());
            return "Failed to download logs from GitHub: " + e.getMessage();
        }
    }

    /**
     * Unzips the downloaded log archive and collects relevant log file contents.
     */
    private String extractLogsFromZip(byte[] zipData) {
        StringBuilder logsBuilder = new StringBuilder();
        int maxChars = 200_000; // Limit extracted logs to ~200KB for memory safety

        try (InputStream byteStream = new ByteArrayInputStream(zipData);
             ZipInputStream zipStream = new ZipInputStream(byteStream)) {

            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".txt")) {
                    byte[] buffer = new byte[4096];
                    int len;
                    StringBuilder fileContent = new StringBuilder();

                    while ((len = zipStream.read(buffer)) > 0) {
                        fileContent.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        if (fileContent.length() > 50_000) {
                            break; // Avoid huge single log files
                        }
                    }

                    String contentStr = fileContent.toString();
                    // If it contains typical error patterns, prioritize it
                    if (contentStr.contains("ERROR") || contentStr.contains("FAIL") ||
                        contentStr.contains("Exception") || contentStr.contains("Error") ||
                        contentStr.contains("error:") || contentStr.contains("failure")) {

                        logsBuilder.append("=== LOG FILE: ").append(entry.getName()).append(" ===\n");
                        logsBuilder.append(contentStr).append("\n\n");
                    }

                    if (logsBuilder.length() >= maxChars) {
                        break;
                    }
                }
                zipStream.closeEntry();
            }
        } catch (Exception e) {
            log.warn("Error unzipping log files: {}", e.getMessage());
        }

        if (logsBuilder.isEmpty()) {
            return "Log archive downloaded, but no error markers found in log entries.";
        }

        return logsBuilder.toString();
    }
}
