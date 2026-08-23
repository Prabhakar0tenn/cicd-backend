package com.selfhealing.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for POST /api/repositories/{id}/branches */
@Getter
@NoArgsConstructor
public class BranchConfigRequest {

    @NotBlank(message = "Branch name is required")
    private String branchName;

    private boolean healingEnabled = true;

    @Min(value = 1, message = "Must allow at least 1 attempt")
    @Max(value = 10, message = "Maximum 10 attempts allowed")
    private int maxAttempts = 3;
}
