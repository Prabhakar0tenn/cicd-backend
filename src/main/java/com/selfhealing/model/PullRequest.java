package com.selfhealing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the Pull Request created by the platform once patch validation passes.
 */
@Document(collection = "pull_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequest {

    @Id
    private String id;

    @Indexed
    private String healingJobId;

    private String repositoryId;

    private int prNumber;

    private String prUrl;

    /** Source branch (e.g. ai-healing/job-123-attempt-1) */
    private String headBranch;

    /** Target branch (e.g. main or feature/branch) */
    private String baseBranch;

    private String title;

    private String status; // OPEN, MERGED, CLOSED

    @CreatedDate
    private Instant createdAt;
}
