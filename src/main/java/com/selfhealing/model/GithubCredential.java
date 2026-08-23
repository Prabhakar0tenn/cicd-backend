package com.selfhealing.model;

import com.selfhealing.enums.CredentialStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Stores the AES-256 encrypted GitHub Personal Access Token for a user.
 *
 * Security rules (enforced at service layer, never break these):
 * 1. encryptedToken is NEVER returned to the frontend — ever.
 * 2. encryptedToken is decrypted ONLY inside CredentialService when
 *    making GitHub API calls. Decrypted value lives in memory, never persisted.
 * 3. The AES encryption key lives in ENCRYPTION_KEY env variable only.
 *
 * Why one-to-one (userId is indexed unique)?
 * One user = one GitHub account for MVP simplicity.
 * Multi-account support can be added later without schema changes.
 */
@Document(collection = "github_credentials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubCredential {

    @Id
    private String id;

    /** References User._id — one credential per user */
    @Indexed(unique = true)
    private String userId;

    /**
     * AES-256-GCM encrypted GitHub PAT.
     * Format: Base64(IV + CipherText)
     * IV is randomly generated per encryption — ensures unique ciphertext each time.
     */
    private String encryptedToken;

    /** GitHub username extracted at token validation time (e.g. "prabhakar") */
    private String githubUsername;

    @CreatedDate
    private Instant tokenCreatedAt;

    private Instant lastValidatedAt;

    @Builder.Default
    private CredentialStatus status = CredentialStatus.ACTIVE;
}
