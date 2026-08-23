package com.selfhealing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a proposed modification to a single source file.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileChange {

    /** Relative file path in the repository (e.g. "src/main/java/com/example/UserService.java") */
    private String filePath;

    /** Operation type: "REPLACE", "CREATE", or "DELETE" */
    @Builder.Default
    private String operation = "REPLACE";

    /** Exact block of code to find and replace */
    private String oldCode;

    /** Replacement block of code */
    private String newCode;
}
