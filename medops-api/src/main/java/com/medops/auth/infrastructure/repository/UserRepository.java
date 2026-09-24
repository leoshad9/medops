package com.medops.auth.infrastructure.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.medops.auth.domain.User;
import com.medops.auth.domain.UserStatus;

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
     * Eagerly loads {@code roles} with the user in one query. The default
     * {@link #findByEmail(String)} leaves {@code roles} lazy, so the auth filter
     * pays 1 (user) + N (roles) queries per authenticated request; this variant
     * is the single-round-trip path for {@code JwtAuthenticationFilter}.
     *
     * @param email the user's email
     * @return Optional containing the user with roles if found
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByEmail(String email);

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
