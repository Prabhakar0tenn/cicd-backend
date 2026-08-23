package com.selfhealing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for a single branch within a MonitoredRepo.
 *
 * Stored as an embedded document inside MonitoredRepo.branches[] —
 * not as a separate collection. We never need to query branches
 * independently of their parent repo.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchConfig {

    /** Branch name — e.g. "main", "develop", "feature/*" */
    private String name;

    /**
     * Whether healing is active for this specific branch.
     * Allows fine-grained control: heal "main" but not "develop".
     */
    @Builder.Default
    private boolean healingEnabled = true;

    /**
     * Maximum number of healing attempts before giving up.
     * Prevents infinite loops on unfixable failures.
     */
    @Builder.Default
    private int maxAttempts = 3;
}
