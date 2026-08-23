package com.selfhealing.repository;

import com.selfhealing.model.MonitoredRepo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for MonitoredRepo documents.
 */
@Repository
public interface MonitoredRepoRepository extends MongoRepository<MonitoredRepo, String> {

    /** All repos the user has connected */
    List<MonitoredRepo> findByUserId(String userId);

    /** Find a specific repo by its owner + name + userId (enforces ownership) */
    Optional<MonitoredRepo> findByUserIdAndOwnerAndName(String userId, String owner, String name);

    /** Check if user already connected this repo */
    boolean existsByUserIdAndOwnerAndName(String userId, String owner, String name);
}
