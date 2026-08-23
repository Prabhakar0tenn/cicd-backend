package com.selfhealing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete context bundle sent to the AI engine for reasoning.
 * Contains failure analysis, commit diffs, and exact source code at failed commit.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureContext {

    private String owner;
    private String repoName;
    private String branch;
    private String failedCommitSha;
    private long workflowRunId;

    /** Parsed failure info from logs */
    private FailureInfo failureInfo;

    /** Git diff between the failed commit and its parent */
    private String commitDiff;

    /** File paths and their full text content at failedCommitSha */
    @Builder.Default
    private Map<String, String> sourceFiles = new HashMap<>();

    /** Summaries of prior attempts for iterative retry enrichment */
    @Builder.Default
    private List<String> previousAttemptFeedback = new ArrayList<>();
}
