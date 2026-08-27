package com.medops.auth.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medops.auth.entity.RefreshToken;

/**
 * Repository for RefreshToken entity.
 * Provides CRUD operations and custom query methods for token management.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Find a refresh token by its hash.
     *
     * @param tokenHash the token hash
     * @return Optional containing the token if found
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Find all valid (not expired and not revoked) tokens for a user.
     *
     * @param userId the user's ID
     * @param now the current timestamp
     * @return list of valid refresh tokens
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId " +
           "AND rt.expiresAt > :now AND rt.revokedAt IS NULL")
    List<RefreshToken> findValidTokensByUserId(@Param("userId") UUID userId, @Param("now") ZonedDateTime now);

    /**
     * Find all tokens for a user (regardless of validity).
     *
     * @param userId the user's ID
     * @return list of all refresh tokens for the user
     */
    List<RefreshToken> findByUserId(UUID userId);

    /**
     * Delete all expired tokens (clean-up operation).
     *
     * @param expiresAt the expiration threshold
     */
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt <= :expiresAt")
    void deleteExpiredTokens(@Param("expiresAt") ZonedDateTime expiresAt);

    /**
     * Revoke all tokens for a user (logout operation).
     *
     * @param userId the user's ID
     * @param now the current timestamp
     */
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now " +
           "WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    void revokeAllUserTokens(@Param("userId") UUID userId, @Param("now") ZonedDateTime now);
}
