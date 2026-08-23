package com.selfhealing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for POST /api/repositories */
@Getter
@NoArgsConstructor
public class AddRepositoryRequest {

    @NotBlank(message = "Owner is required")
    private String owner;  // e.g. "prabhakar" or "acme-corp"

    @NotBlank(message = "Repository name is required")
    private String name;   // e.g. "my-api"
}
