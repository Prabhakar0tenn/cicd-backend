package com.selfhealing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload sent by GitHub Actions workflow on CI failure:
 * POST /api/healing/failure
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureCallbackRequest {

    @NotBlank(message = "Repository name is required")
    private String repository;

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotBlank(message = "Branch is required")
    private String branch;

    @NotBlank(message = "Commit SHA is required")
    private String commitSha;

    @NotNull(message = "Workflow run ID is required")
    private Long workflowRunId;
}
