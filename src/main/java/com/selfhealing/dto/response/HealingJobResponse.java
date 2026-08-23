package com.selfhealing.dto.response;

import com.selfhealing.enums.FailureType;
import com.selfhealing.enums.HealingStatus;
import com.selfhealing.model.HealingJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealingJobResponse {

    private String id;
    private String repositoryId;
    private String owner;
    private String repoName;
    private String branch;
    private String failedCommitSha;
    private long workflowRunId;
    private FailureType failureType;
    private String failureSummary;
    private HealingStatus status;
    private int attemptCount;
    private int maxAttempts;
    private String healingBranch;
    private String prUrl;
    private Integer prNumber;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public static HealingJobResponse from(HealingJob job) {
        return HealingJobResponse.builder()
                .id(job.getId())
                .repositoryId(job.getRepositoryId())
                .owner(job.getOwner())
                .repoName(job.getRepoName())
                .branch(job.getBranch())
                .failedCommitSha(job.getFailedCommitSha())
                .workflowRunId(job.getWorkflowRunId())
                .failureType(job.getFailureType())
                .failureSummary(job.getFailureSummary())
                .status(job.getStatus())
                .attemptCount(job.getAttemptCount())
                .maxAttempts(job.getMaxAttempts())
                .healingBranch(job.getHealingBranch())
                .prUrl(job.getPrUrl())
                .prNumber(job.getPrNumber())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
