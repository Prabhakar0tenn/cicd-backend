package com.selfhealing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption for sensitive values (GitHub PATs).
 *
 * Why AES-GCM instead of AES-CBC?
 * GCM (Galois/Counter Mode) provides AUTHENTICATED encryption:
 * it detects tampering (ciphertext modification) in addition to ensuring
 * confidentiality. CBC only provides confidentiality.
 *
 * Storage format: Base64( IV[12 bytes] + CipherText[N bytes] + AuthTag[16 bytes] )
 * The IV is randomly generated per encryption — the same plaintext encrypts
 * to different ciphertexts each time, preventing pattern analysis.
 *
 * The SecretKey bean is provided by EncryptionConfig.
 * The encryption key itself lives ONLY in the ENCRYPTION_KEY environment variable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;       // 96 bits — recommended for GCM
    private static final int GCM_AUTH_TAG_BITS = 128;  // Maximum tag length

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypts a plain-text value (e.g., a GitHub PAT).
     *
     * @param plaintext The value to encrypt
     * @return Base64-encoded string containing IV + CipherText (safe to store in MongoDB)
     */
    public String encrypt(String plaintext) {
        try {
            // Generate a random 12-byte IV for this specific encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_AUTH_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes());

            // Prepend IV to ciphertext so we can extract it during decryption
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            // Never expose encryption internals in error messages
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a value previously encrypted by this service.
     *
     * @param encryptedValue The Base64-encoded IV + CipherText from MongoDB
     * @return The original plain-text value
     * @throws RuntimeException if the ciphertext has been tampered with or the key is wrong
     */
    public String decrypt(String encryptedValue) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedValue);

            // Extract the IV from the first 12 bytes
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            // The rest is the ciphertext + GCM auth tag
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_AUTH_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed — possible key mismatch or data corruption", e);
        }
    }
}
