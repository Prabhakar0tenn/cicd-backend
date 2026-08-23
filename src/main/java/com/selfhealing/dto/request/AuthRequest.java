package com.selfhealing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for POST /api/auth/register and POST /api/auth/login */
@Getter
@NoArgsConstructor
public class AuthRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 32, message = "Username must be 3–32 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
