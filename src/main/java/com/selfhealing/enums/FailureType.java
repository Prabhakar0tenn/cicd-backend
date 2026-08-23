package com.selfhealing.enums;

/**
 * Categories of CI failure that our system can analyze and attempt to fix.
 *
 * MVP supports BUILD and TEST failures.
 * LINT is supported but lower priority — AI may have less confidence in fixes.
 */
public enum FailureType {

    /** Compilation failed — syntax error, missing import, type mismatch, etc. */
    BUILD,

    /** Tests ran but one or more failed — assertion error, NPE, unexpected exception. */
    TEST,

    /** Linter/static analyzer reported violations — Checkstyle, ESLint, PMD, etc. */
    LINT,

    /**
     * Could not determine the failure type from the logs.
     * The healing engine will still try but with lower confidence.
     */
    UNKNOWN
}
