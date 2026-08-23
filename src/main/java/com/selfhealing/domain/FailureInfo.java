package com.selfhealing.domain;

import com.selfhealing.enums.FailureType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured failure data extracted from GitHub Actions build/test logs.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureInfo {

    @Builder.Default
    private FailureType failureType = FailureType.UNKNOWN;

    /** The specific job or step that failed (e.g., "build", "run-tests", "mvn test") */
    private String failedStep;

    /** Name of the failing test class or test method (if TEST failure) */
    private String failingTest;

    /** Primary error message or compiler error */
    private String errorMessage;

    /** Extracted relevant stack trace snippet */
    private String stackTrace;

    /** File paths mentioned in the compiler/test error output (e.g., src/main/java/Foo.java:42) */
    @Builder.Default
    private List<String> errorLocations = new ArrayList<>();

    /** Raw log excerpt around the error */
    private String rawLogSnippet;
}
