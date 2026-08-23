package com.selfhealing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Handles all JWT operations: token generation and validation.
 *
 * We use HS256 (HMAC-SHA256) — a symmetric algorithm where the same
 * secret key signs and verifies the token. This is appropriate for
 * a single-service setup. Multi-service setups would use RS256 (asymmetric).
 *
 * Token contents (claims):
 * - sub: the user's MongoDB _id (used to look up the user on each request)
 * - iat: issued-at timestamp
 * - exp: expiry timestamp (24 hours from issue)
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {

        // Derive a SecretKey from the raw secret string
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT token for the given user ID.
     *
     * @param userId The MongoDB _id of the authenticated user
     * @return A compact JWT string (header.payload.signature)
     */
    public String generateToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extracts the user ID from a valid token.
     *
     * @param token The raw JWT string (without "Bearer " prefix)
     * @return The user ID stored in the token's subject claim
     * @throws JwtException if the token is invalid or expired
     */
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Returns true if the token is well-formed, signed correctly, and not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("JWT validation failed: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * Parses and returns all claims from the token.
     * This method throws a JwtException if the token is invalid.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
