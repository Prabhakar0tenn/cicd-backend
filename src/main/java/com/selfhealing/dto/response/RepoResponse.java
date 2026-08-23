package com.selfhealing.dto.response;

import com.selfhealing.model.BranchConfig;
import com.selfhealing.model.MonitoredRepo;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Returned to the frontend when listing or adding repositories.
 * Safe to expose — contains NO secrets (no encryptedToken, no internal IDs).
 */
@Getter
@Builder
public class RepoResponse {

    private String id;
    private String owner;
    private String name;
    private String fullName;          // "owner/name"
    private boolean autoHealEnabled;
    private List<BranchConfig> branches;
    private Instant connectedAt;

    /** Factory method — converts MonitoredRepo domain object to response DTO */
    public static RepoResponse from(MonitoredRepo repo) {
        return RepoResponse.builder()
                .id(repo.getId())
                .owner(repo.getOwner())
                .name(repo.getName())
                .fullName(repo.getFullName())
                .autoHealEnabled(repo.isAutoHealEnabled())
                .branches(repo.getBranches())
                .connectedAt(repo.getConnectedAt())
                .build();
    }
}
