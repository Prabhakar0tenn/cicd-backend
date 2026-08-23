package com.selfhealing.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Returned after successful login or registration */
@Getter
@Builder
public class AuthResponse {
    private String token;
    private String username;
}
