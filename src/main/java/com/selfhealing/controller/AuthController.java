package com.selfhealing.controller;

import com.selfhealing.dto.request.AuthRequest;
import com.selfhealing.dto.response.ApiResponse;
import com.selfhealing.dto.response.AuthResponse;
import com.selfhealing.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller — public endpoints (no JWT required).
 *
 * POST /api/auth/register — create a new account, returns JWT
 * POST /api/auth/login    — authenticate, returns JWT
 *
 * Both endpoints are listed in SecurityConfig as public ("/api/auth/**").
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody AuthRequest request) {
        String token = authService.register(request.getUsername(), request.getPassword());

        AuthResponse authResponse = AuthResponse.builder()
                .token(token)
                .username(request.getUsername())
                .build();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());

        AuthResponse authResponse = AuthResponse.builder()
                .token(token)
                .username(request.getUsername())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }
}
