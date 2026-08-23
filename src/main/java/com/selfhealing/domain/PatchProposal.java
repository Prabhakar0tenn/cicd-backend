package com.selfhealing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured AI response containing diagnosis and proposed file patches.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchProposal {

    /** AI diagnosis of the root cause */
    private String rootCause;

    /** Model confidence score between 0.0 and 1.0 */
    @Builder.Default
    private double confidence = 0.8;

    /** Detailed reasoning behind the fix */
    private String reasoning;

    /** List of targeted file modifications */
    @Builder.Default
    private List<FileChange> changes = new ArrayList<>();

    /** Recommended tests or checks to verify the fix */
    @Builder.Default
    private List<String> testsToRun = new ArrayList<>();
}
