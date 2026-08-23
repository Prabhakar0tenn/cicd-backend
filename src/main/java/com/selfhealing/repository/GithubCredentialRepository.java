package com.selfhealing.repository;

import com.selfhealing.model.GithubCredential;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data repository for GithubCredential documents.
 */
@Repository
public interface GithubCredentialRepository extends MongoRepository<GithubCredential, String> {

    /** Returns this user's stored GitHub credential, if any */
    Optional<GithubCredential> findByUserId(String userId);

    /** Used to check if a user has already connected GitHub */
    boolean existsByUserId(String userId);
}
