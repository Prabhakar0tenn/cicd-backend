package com.selfhealing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Validates the X-Healer-Secret header on incoming GitHub Actions callbacks.
 *
 * Why a separate secret for callbacks?
 * The callback endpoint (/api/healing/failure) is public — it cannot require
 * a user JWT because GitHub Actions is the caller, not a browser.
 * Instead, we use a shared secret: we know it, and the user stores it
 * in their repo's GitHub Secrets as HEALER_SECRET.
 *
 * This prevents random internet actors from triggering fake healing jobs.
 */
@Service
public class CallbackAuthService {

    private final String expectedSecret;

    public CallbackAuthService(@Value("${app.healing.callback-secret}") String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    /**
     * Returns true if the provided secret matches the expected callback secret.
     * Uses a constant-time comparison to prevent timing attacks.
     *
     * @param providedSecret The value from the X-Healer-Secret header
     */
    public boolean isValidCallbackSecret(String providedSecret) {
        if (providedSecret == null || expectedSecret == null) {
            return false;
        }

        // MessageDigest.isEqual provides constant-time comparison
        // — prevents attackers from guessing the secret byte-by-byte via timing
        return java.security.MessageDigest.isEqual(
                providedSecret.getBytes(),
                expectedSecret.getBytes()
        );
    }
}
