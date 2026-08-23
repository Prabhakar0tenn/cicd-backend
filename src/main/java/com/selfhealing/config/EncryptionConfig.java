package com.selfhealing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * AES-256 encryption configuration for GitHub PAT storage.
 *
 * Why AES and not hashing?
 * We need to DECRYPT the PAT later to use it in GitHub API calls.
 * Hashing is one-way — you cannot recover the original value.
 * AES-256 with a secret key allows secure, reversible encryption.
 *
 * The encryption key lives ONLY in the ENCRYPTION_KEY environment variable.
 * It is NEVER stored in MongoDB or logged anywhere.
 */
@Configuration
public class EncryptionConfig {

    /**
     * The AES-256 secret key, injected from the ENCRYPTION_KEY environment variable.
     * Must be a Base64-encoded 32-byte (256-bit) value.
     *
     * Generate a good key with: openssl rand -base64 32
     */
    @Value("${app.encryption.key}")
    private String base64EncodedKey;

    /**
     * Provides the AES SecretKey bean that CredentialService uses
     * to encrypt and decrypt GitHub PATs.
     */
    @Bean
    public SecretKey aesSecretKey() {
        byte[] decodedKey = Base64.getDecoder().decode(base64EncodedKey);

        if (decodedKey.length != 32) {
            throw new IllegalStateException(
                "ENCRYPTION_KEY must be a Base64-encoded 32-byte (256-bit) key. " +
                "Generate one with: openssl rand -base64 32"
            );
        }

        return new SecretKeySpec(decodedKey, "AES");
    }
}
