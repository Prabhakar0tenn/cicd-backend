package com.selfhealing.model;

import com.selfhealing.enums.FailureType;
import com.selfhealing.enums.HealingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the complete lifecycle of a single CI healing workflow.
 *
 * One HealingJob is created whenever a monitored branch CI fails.
 * It coordinates log analysis, AI reasoning, patch validation,
 * and PR creation across up to N retry attempts.
 */
@Document(collection = "healing_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealingJob {

    @Id
    private String id;

    @Indexed
    private String repositoryId;

    private String userId;

    private String owner;

    private String repoName;

    private String branch;

    private String failedCommitSha;

    private long workflowRunId;

    @Builder.Default
    private FailureType failureType = FailureType.UNKNOWN;

    private String failureSummary;

    @Builder.Default
    private HealingStatus status = HealingStatus.DETECTED;

    @Builder.Default
    private int attemptCount = 0;

    @Builder.Default
    private int maxAttempts = 3;

    private String healingBranch;

    private String prUrl;

    private Integer prNumber;

    private String errorMessage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
