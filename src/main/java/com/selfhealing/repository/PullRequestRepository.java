package com.selfhealing.repository;

import com.selfhealing.model.PullRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PullRequestRepository extends MongoRepository<PullRequest, String> {

    Optional<PullRequest> findByHealingJobId(String healingJobId);
}
