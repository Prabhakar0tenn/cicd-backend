package com.selfhealing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Spring Security filter that runs on every HTTP request (exactly once).
 *
 * What it does:
 * 1. Reads the "Authorization: Bearer <token>" header
 * 2. Validates the JWT token using JwtService
 * 3. If valid, sets the authentication in the SecurityContext
 *    so Spring Security knows the request is authenticated
 *
 * If the token is missing or invalid, the filter does nothing —
 * Spring Security's access rules then reject the request with 401.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        // If no token is present, just continue — let Spring Security's
        // authorization rules handle the rejection for protected endpoints
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtService.isTokenValid(token)) {
            log.debug("Invalid JWT token received for request: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtService.extractUserId(token);

        // Mark the request as authenticated in Spring's security context
        // No roles/authorities needed for MVP — all authenticated users have equal access
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT from the Authorization header.
     * Returns null if the header is missing or not in "Bearer <token>" format.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }

        return null;
    }
}
