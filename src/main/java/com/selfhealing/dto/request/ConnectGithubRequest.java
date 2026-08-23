package com.selfhealing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for POST /api/github/connect — stores the user's GitHub PAT */
@Getter
@NoArgsConstructor
public class ConnectGithubRequest {

    /**
     * The raw GitHub Personal Access Token.
     * This value is validated against GitHub's API, then immediately encrypted
     * with AES-256 before being stored. It is NEVER logged or returned.
     *
     * Required scopes: repo, workflow, read:user
     */
    @NotBlank(message = "GitHub token is required")
    private String token;
}
