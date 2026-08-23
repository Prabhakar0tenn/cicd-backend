package com.selfhealing.repository;

import com.selfhealing.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data repository for User documents.
 * Spring auto-generates the implementation — we only define the query methods.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /** Used during login to find user by username */
    Optional<User> findByUsername(String username);

    /** Used during registration to check if username is taken */
    boolean existsByUsername(String username);
}
