package com.selfhealing.enums;

/**
 * Outcome of running CI on a healing branch.
 */
public enum ValidationResult {

    /** All CI checks passed on the healing branch. Ready to create PR. */
    PASS,

    /** One or more CI checks failed on the healing branch. May retry. */
    FAIL,

    /**
     * GitHub Actions did not complete within the allowed timeout window.
     * Treated the same as FAIL for retry logic purposes.
     */
    TIMEOUT
}
