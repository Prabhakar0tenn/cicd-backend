package com.selfhealing.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfhealing.domain.FailureContext;
import com.selfhealing.domain.PatchProposal;
import com.selfhealing.exception.AiProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of AiProvider using gemini-2.5-flash.
 * Constructs prompt with system constraints and parses structured JSON output.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class GeminiAiProvider implements AiProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-2.5-flash}")
    private String modelName;

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Override
    public PatchProposal generatePatch(FailureContext context) {
        log.info("Requesting patch proposal from Gemini model: {}", modelName);

        String prompt = buildPrompt(context);
        String url = String.format(GEMINI_API_URL, modelName, apiKey);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AiProviderException("Gemini API call failed with status: " + response.getStatusCode());
            }

            return parseGeminiResponse(response.getBody());
        } catch (Exception e) {
            log.error("Gemini AI generation failed: {}", e.getMessage(), e);
            throw new AiProviderException("Failed to generate patch from Gemini: " + e.getMessage(), e);
        }
    }

    /**
     * Constructs the structured prompt with all failure diagnostics.
     */
    private String buildPrompt(FailureContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert Autonomous CI/CD Self-Healing Engineer.\n");
        sb.append("Your task is to analyze a Continuous Integration build/test failure and produce a targeted, surgical fix.\n\n");

        sb.append("### REPOSITORY & FAILURE METRICS:\n");
        sb.append("Repository: ").append(context.getOwner()).append("/").append(context.getRepoName()).append("\n");
        sb.append("Branch: ").append(context.getBranch()).append("\n");
        sb.append("Failed Commit: ").append(context.getFailedCommitSha()).append("\n");
        sb.append("Failure Category: ").append(context.getFailureInfo().getFailureType()).append("\n");
        sb.append("Failed Step: ").append(context.getFailureInfo().getFailedStep()).append("\n");
        sb.append("Error Message: ").append(context.getFailureInfo().getErrorMessage()).append("\n");
        if (context.getFailureInfo().getFailingTest() != null) {
            sb.append("Failing Test: ").append(context.getFailureInfo().getFailingTest()).append("\n");
        }
        sb.append("\n");

        if (context.getPreviousAttemptFeedback() != null && !context.getPreviousAttemptFeedback().isEmpty()) {
            sb.append("### PRIOR ATTEMPT FEEDBACK (Please avoid repeating these failures):\n");
            for (String feedback : context.getPreviousAttemptFeedback()) {
                sb.append("- ").append(feedback).append("\n");
            }
            sb.append("\n");
        }

        sb.append("### RECENT COMMIT DIFF:\n```diff\n");
        sb.append(context.getCommitDiff() != null ? context.getCommitDiff() : "No diff available");
        sb.append("\n```\n\n");

        sb.append("### CI ERROR LOG SNIPPET:\n```text\n");
        sb.append(context.getFailureInfo().getRawLogSnippet() != null ? context.getFailureInfo().getRawLogSnippet() : "");
        sb.append("\n```\n\n");

        sb.append("### SOURCE CODE AT FAILED COMMIT:\n");
        if (context.getSourceFiles() != null && !context.getSourceFiles().isEmpty()) {
            context.getSourceFiles().forEach((path, content) -> {
                sb.append("--- FILE: ").append(path).append(" ---\n```\n");
                sb.append(content);
                sb.append("\n```\n\n");
            });
        } else {
            sb.append("No source files loaded.\n\n");
        }

        sb.append("### HARD CONSTRAINTS & RULES:\n");
        sb.append("1. Make minimal, surgical modifications. Do not rewrite unaffected code.\n");
        sb.append("2. In each change, 'oldCode' MUST be an EXACT character-for-character substring existing in the corresponding source file.\n");
        sb.append("3. 'newCode' must be a direct replacement for 'oldCode' that fixes the compiler or test failure.\n");
        sb.append("4. Never modify security credentials, secrets, or .env files.\n");
        sb.append("5. Respond ONLY in valid JSON matching this schema:\n\n");

        sb.append("{\n");
        sb.append("  \"rootCause\": \"concise 1-2 sentence explanation of what broke and why\",\n");
        sb.append("  \"confidence\": 0.95,\n");
        sb.append("  \"reasoning\": \"detailed explanation of the fix\",\n");
        sb.append("  \"changes\": [\n");
        sb.append("    {\n");
        sb.append("      \"filePath\": \"relative/path/to/file.ext\",\n");
        sb.append("      \"operation\": \"REPLACE\",\n");
        sb.append("      \"oldCode\": \"exact string to replace\",\n");
        sb.append("      \"newCode\": \"replacement code\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"testsToRun\": [\"test command or name\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Parses the response from the Gemini API and extracts the PatchProposal.
     */
    private PatchProposal parseGeminiResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new AiProviderException("Gemini returned empty candidate content");
            }

            String rawText = textNode.asText().trim();

            // Strip markdown code fences if Gemini included them
            if (rawText.startsWith("```json")) {
                rawText = rawText.substring(7);
            } else if (rawText.startsWith("```")) {
                rawText = rawText.substring(3);
            }
            if (rawText.endsWith("```")) {
                rawText = rawText.substring(0, rawText.length() - 3);
            }
            rawText = rawText.trim();

            return objectMapper.readValue(rawText, PatchProposal.class);
        } catch (Exception e) {
            log.error("Failed to parse Gemini output: {}", e.getMessage());
            throw new AiProviderException("Failed to deserialize PatchProposal from Gemini: " + e.getMessage(), e);
        }
    }
}
