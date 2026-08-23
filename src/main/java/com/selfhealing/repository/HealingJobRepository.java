package com.selfhealing.repository;

import com.selfhealing.enums.HealingStatus;
import com.selfhealing.model.HealingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HealingJobRepository extends MongoRepository<HealingJob, String> {

    Page<HealingJob> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<HealingJob> findByRepositoryIdOrderByCreatedAtDesc(String repositoryId);

    Optional<HealingJob> findByIdAndUserId(String id, String userId);

    List<HealingJob> findByStatus(HealingStatus status);

    boolean existsByRepositoryIdAndBranchAndFailedCommitShaAndStatusNotIn(
            String repositoryId, String branch, String failedCommitSha, List<HealingStatus> finalStatuses);
}
