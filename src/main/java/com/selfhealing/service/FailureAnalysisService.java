package com.selfhealing.service;

import com.selfhealing.domain.FailureInfo;
import com.selfhealing.enums.FailureType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw CI log output from GitHub Actions to extract structured failure metrics:
 * failure type, stack trace, error message, failing test, and affected source file paths.
 */
@Service
@Slf4j
public class FailureAnalysisService {

    // Regex to match source file paths with optional line numbers (Java, JS/TS, Python, Go, Rust)
    private static final Pattern FILE_LOCATION_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_\\-./\\\\]+\\.(?:java|kt|scala|ts|js|jsx|tsx|py|go|rs|cpp|c|cs|rb))(?::\\[|:|\\[)(\\d+)(?:,(\\d+)\\]|:|\\])?",
            Pattern.CASE_INSENSITIVE
    );

    // Regex to detect test failures
    private static final Pattern TEST_FAILURE_PATTERN = Pattern.compile(
            "(?:FAILURE|FAILED|Failures:|Error:)\\s+([a-zA-Z0-9_$.#]+(?:Test|Spec|test[a-zA-Z0-9_]*))",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Analyzes raw log text and returns a structured FailureInfo.
     *
     * @param rawLogs Raw text extracted from GitHub Actions step logs
     * @param failedStep Name of the step that failed
     * @return Structured FailureInfo
     */
    public FailureInfo parseFailure(String rawLogs, String failedStep) {
        if (rawLogs == null || rawLogs.isBlank()) {
            return FailureInfo.builder()
                    .failureType(FailureType.UNKNOWN)
                    .failedStep(failedStep)
                    .errorMessage("No logs available for analysis")
                    .build();
        }

        FailureType failureType = detectFailureType(rawLogs, failedStep);
        String errorMessage = extractPrimaryErrorMessage(rawLogs);
        String stackTrace = extractStackTraceSnippet(rawLogs);
        String failingTest = extractFailingTestName(rawLogs);
        List<String> errorLocations = extractFileLocations(rawLogs);
        String logSnippet = extractRelevantLogSnippet(rawLogs);

        return FailureInfo.builder()
                .failureType(failureType)
                .failedStep(failedStep)
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .failingTest(failingTest)
                .errorLocations(errorLocations)
                .rawLogSnippet(logSnippet)
                .build();
    }

    /**
     * Determines whether the failure is BUILD, TEST, LINT, or UNKNOWN.
     */
    private FailureType detectFailureType(String logs, String stepName) {
        String lowerLogs = logs.toLowerCase();
        String lowerStep = stepName != null ? stepName.toLowerCase() : "";

        // Check if the step explicitly mentions test or lint
        if (lowerStep.contains("test") || lowerStep.contains("surefire") || lowerStep.contains("pytest") || lowerStep.contains("jest")) {
            return FailureType.TEST;
        }
        if (lowerStep.contains("lint") || lowerStep.contains("checkstyle") || lowerStep.contains("eslint") || lowerStep.contains("flake8")) {
            return FailureType.LINT;
        }

        // Check log content
        if (lowerLogs.contains("compilation failure") || lowerLogs.contains("compilation error") ||
            lowerLogs.contains("cannot find symbol") || lowerLogs.contains("syntaxerror") ||
            lowerLogs.contains("type error") || lowerLogs.contains("error TS") || lowerLogs.contains("package does not exist")) {
            return FailureType.BUILD;
        }

        if (lowerLogs.contains("there are test failures") || lowerLogs.contains("failures: ") ||
            lowerLogs.contains("assertionerror") || lowerLogs.contains("tests run:") ||
            lowerLogs.contains("failed test") || lowerLogs.contains("test failed")) {
            return FailureType.TEST;
        }

        if (lowerLogs.contains("checkstyle") || lowerLogs.contains("eslint") || lowerLogs.contains("lint error")) {
            return FailureType.LINT;
        }

        return FailureType.BUILD; // Default to BUILD for generic CI failures
    }

    /**
     * Extracts the most prominent error message line from the logs.
     */
    private String extractPrimaryErrorMessage(String logs) {
        String[] lines = logs.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[ERROR]") && !trimmed.equals("[ERROR]") && !trimmed.contains("re-run Maven")) {
                return trimmed.replace("[ERROR]", "").trim();
            }
            if (trimmed.startsWith("Error:") || trimmed.startsWith("FAILED:")) {
                return trimmed;
            }
            if (trimmed.contains("Exception:") || trimmed.contains("AssertionError:")) {
                return trimmed;
            }
        }
        return "CI command exited with non-zero status";
    }

    /**
     * Extracts an excerpt of the stack trace or compiler error lines.
     */
    private String extractStackTraceSnippet(String logs) {
        String[] lines = logs.split("\\r?\\n");
        StringBuilder trace = new StringBuilder();
        boolean capturing = false;
        int capturedLines = 0;

        for (String line : lines) {
            if (line.contains("Exception") || line.contains("Error:") || line.contains("[ERROR]") ||
                line.contains("at ") || line.contains("FAILED") || line.contains("AssertionError")) {
                capturing = true;
            }

            if (capturing) {
                trace.append(line).append("\n");
                capturedLines++;
                if (capturedLines >= 60) { // Limit stack trace to 60 relevant lines
                    break;
                }
            }
        }

        return trace.isEmpty() ? "No specific stack trace found in logs." : trace.toString().trim();
    }

    /**
     * Extracts the failing test name (if any).
     */
    private String extractFailingTestName(String logs) {
        Matcher matcher = TEST_FAILURE_PATTERN.matcher(logs);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts source file paths mentioned in compilation errors or stack traces.
     */
    private List<String> extractFileLocations(String logs) {
        Set<String> filePaths = new HashSet<>();
        Matcher matcher = FILE_LOCATION_PATTERN.matcher(logs);

        while (matcher.find()) {
            String path = matcher.group(1);
            // Ignore system libraries or common third-party paths
            if (!path.contains("node_modules") && !path.contains(".m2") && !path.contains("/usr/") && !path.contains("jdk")) {
                filePaths.add(path.replace('\\', '/'));
            }
        }

        return new ArrayList<>(filePaths);
    }

    /**
     * Extracts a clean 100-line excerpt around the error for AI context.
     */
    private String extractRelevantLogSnippet(String logs) {
        String[] lines = logs.split("\\r?\\n");
        int totalLines = lines.length;
        if (totalLines <= 120) {
            return logs;
        }

        // Find the index of the first significant error
        int errorIndex = -1;
        for (int i = 0; i < totalLines; i++) {
            if (lines[i].contains("ERROR") || lines[i].contains("FAIL") || lines[i].contains("Exception")) {
                errorIndex = i;
                break;
            }
        }

        if (errorIndex == -1) {
            errorIndex = Math.max(0, totalLines - 100);
        }

        int start = Math.max(0, errorIndex - 20);
        int end = Math.min(totalLines, errorIndex + 80);

        StringBuilder snippet = new StringBuilder();
        for (int i = start; i < end; i++) {
            snippet.append(lines[i]).append("\n");
        }

        return snippet.toString();
    }
}
