package com.medops.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medops.auth.entity.User;
import com.medops.auth.entity.UserStatus;

/**
 * Repository for User entity.
 * Provides CRUD operations and custom query methods for user lookup.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by email address.
     *
     * @param email the user's email
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user with the given email exists.
     *
     * @param email the email to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find a user by email and status.
     *
     * @param email the user's email
     * @param status the user's status
     * @return Optional containing the user if found
     */
    Optional<User> findByEmailAndStatus(String email, UserStatus status);
}
