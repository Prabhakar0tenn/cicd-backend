package com.selfhealing.enums;

/**
 * Status of a stored GitHub Personal Access Token.
 */
public enum CredentialStatus {

    /** Token is valid and working. */
    ACTIVE,

    /** Token failed validation (revoked, wrong permissions, etc.) */
    INVALID,

    /** Token has expired (fine-grained PATs have expiry dates). */
    EXPIRED
}
