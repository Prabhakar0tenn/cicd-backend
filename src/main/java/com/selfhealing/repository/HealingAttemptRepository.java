package com.selfhealing.repository;

import com.selfhealing.model.HealingAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealingAttemptRepository extends MongoRepository<HealingAttempt, String> {

    List<HealingAttempt> findByHealingJobIdOrderByAttemptNumberAsc(String healingJobId);

    int countByHealingJobId(String healingJobId);
}
