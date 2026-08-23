package com.selfhealing.service;

import com.selfhealing.domain.FailureContext;
import com.selfhealing.domain.FileChange;
import com.selfhealing.domain.PatchProposal;
import com.selfhealing.exception.PatchValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates the safety and consistency of AI proposed patches before committing.
 *
 * Hard safety checks:
 * 1. Path traversal attacks (reject "../", absolute paths)
 * 2. Secrets protection (reject edits to .env, keys, credentials)
 * 3. File existence (file must be known in the context or repository)
 * 4. Content match (for REPLACE operations, oldCode must exist in original source)
 */
@Service
@Slf4j
public class PatchValidationService {

    private static final Set<String> FORBIDDEN_FILENAMES = Set.of(
            ".env", ".env.local", "id_rsa", "id_ed25519", "credentials.json", "secret.properties"
    );

    /**
     * Validates all file changes in the patch proposal against the original source code.
     *
     * @param proposal Patch proposed by the AI
     * @param context Failure diagnostic context
     * @return Map of filePath -> full new patched content string
     * @throws PatchValidationException if any safety check fails
     */
    public Map<String, String> validateAndApplyChanges(PatchProposal proposal, FailureContext context) {
        if (proposal.getChanges() == null || proposal.getChanges().isEmpty()) {
            throw new PatchValidationException("AI returned no file changes in patch proposal");
        }

        Map<String, String> patchedFiles = new HashMap<>();

        for (FileChange change : proposal.getChanges()) {
            String filePath = change.getFilePath();

            // 1. Path safety check
            validateFilePath(filePath);

            String operation = change.getOperation() != null ? change.getOperation().toUpperCase() : "REPLACE";
            String originalContent = context.getSourceFiles().get(filePath);

            if ("CREATE".equals(operation)) {
                if (change.getNewCode() == null || change.getNewCode().isBlank()) {
                    throw new PatchValidationException("CREATE operation on " + filePath + " had empty newCode");
                }
                patchedFiles.put(filePath, change.getNewCode());

            } else if ("REPLACE".equals(operation)) {
                if (originalContent == null) {
                    throw new PatchValidationException("Cannot replace code in " + filePath + " because file content is missing from context");
                }

                String oldCode = change.getOldCode();
                String newCode = change.getNewCode();

                if (oldCode == null || oldCode.isBlank()) {
                    throw new PatchValidationException("REPLACE operation on " + filePath + " specified empty oldCode");
                }
                if (newCode == null) {
                    newCode = "";
                }

                // Check that oldCode exists in the original file
                if (!originalContent.contains(oldCode)) {
                    // Try normalized line endings (CRLF vs LF)
                    String normalizedOriginal = originalContent.replace("\r\n", "\n");
                    String normalizedOld = oldCode.replace("\r\n", "\n");

                    if (!normalizedOriginal.contains(normalizedOld)) {
                        log.error("Old code not found in {}. OldCode:\n{}", filePath, oldCode);
                        throw new PatchValidationException("Specified oldCode not found in file: " + filePath);
                    }

                    // Apply replacement with normalized text
                    String updatedContent = normalizedOriginal.replace(normalizedOld, newCode.replace("\r\n", "\n"));
                    patchedFiles.put(filePath, updatedContent);
                } else {
                    String updatedContent = originalContent.replace(oldCode, newCode);
                    patchedFiles.put(filePath, updatedContent);
                }
            } else {
                throw new PatchValidationException("Unsupported patch operation: " + operation);
            }
        }

        log.info("Patch validation passed for {} file(s)", patchedFiles.size());
        return patchedFiles;
    }

    /**
     * Validates that the file path does not contain directory traversal or sensitive paths.
     */
    private void validateFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new PatchValidationException("File path in patch cannot be empty");
        }

        if (filePath.contains("..") || filePath.startsWith("/") || filePath.startsWith("\\")) {
            throw new PatchValidationException("Unsafe file path (traversal detected): " + filePath);
        }

        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1).toLowerCase();
        if (FORBIDDEN_FILENAMES.contains(fileName) || fileName.endsWith(".key") || fileName.endsWith(".pem")) {
            throw new PatchValidationException("Modification of sensitive file forbidden: " + filePath);
        }
    }
}
