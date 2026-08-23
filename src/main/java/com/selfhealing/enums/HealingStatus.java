package com.selfhealing.enums;

/**
 * Lifecycle states of a HealingJob.
 *
 * The state machine flows:
 *
 *   DETECTED → ANALYZING → CONTEXT_BUILDING → AI_ANALYSIS
 *       → PATCH_GENERATED → VALIDATING
 *           → SUCCESS → PR_CREATED     (happy path)
 *           → RETRYING → ... (up to MAX_ATTEMPTS times)
 *           → FAILED                   (all attempts exhausted)
 *
 *   ABORTED: original branch changed mid-healing, job cancelled
 */
public enum HealingStatus {

    /** Failure callback received. HealingJob created. Async worker not yet started. */
    DETECTED,

    /** Async worker started. Fetching workflow run and logs from GitHub. */
    ANALYZING,

    /** Logs parsed. Now fetching commit diff and relevant source files. */
    CONTEXT_BUILDING,

    /** FailureContext assembled. Request sent to AI provider. Waiting for response. */
    AI_ANALYSIS,

    /** AI returned a valid PatchProposal. Safety validation passed. */
    PATCH_GENERATED,

    /** Healing branch created and patch committed. GitHub Actions running on healing branch. */
    VALIDATING,

    /** Healing branch CI failed. Preparing for next attempt with enriched context. */
    RETRYING,

    /** Healing branch CI passed. About to create Pull Request. */
    SUCCESS,

    /** Pull Request created successfully. Human review required. */
    PR_CREATED,

    /** All attempts exhausted. Could not fix the failure automatically. */
    FAILED,

    /**
     * Original branch changed while healing was in progress.
     * Job cancelled to avoid applying a patch to a stale commit.
     */
    ABORTED
}
