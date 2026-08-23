package com.selfhealing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A GitHub repository that a user has connected to the platform for monitoring.
 *
 * Note: Named "MonitoredRepo" instead of "Repository" to avoid collision
 * with Spring Data's org.springframework.data.repository.Repository interface.
 *
 * Compound index on (userId + owner + name) enforces uniqueness:
 * the same user cannot add the same repo twice.
 * Different users CAN monitor the same repo independently.
 */
@Document(collection = "repositories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "user_repo_unique", def = "{'userId': 1, 'owner': 1, 'name': 1}", unique = true)
})
public class MonitoredRepo {

    @Id
    private String id;

    /** Who owns this monitoring config */
    private String userId;

    /** GitHub repo owner — could be a user or an org (e.g. "prabhakar" or "acme-corp") */
    private String owner;

    /** GitHub repo name (e.g. "my-api") */
    private String name;

    /**
     * Branch configurations — which branches to watch and their settings.
     * Stored embedded because branch config is always accessed with the repo.
     */
    @Builder.Default
    private List<BranchConfig> branches = new ArrayList<>();

    /**
     * If true, the platform will automatically attempt to heal failures.
     * If false, it will only detect and report (useful for repos the user wants
     * to review before any changes are made).
     */
    @Builder.Default
    private boolean autoHealEnabled = true;

    /** GitHub repo full name for display (e.g. "prabhakar/my-api") */
    public String getFullName() {
        return owner + "/" + name;
    }

    @CreatedDate
    private Instant connectedAt;

    @LastModifiedDate
    private Instant updatedAt;
}
