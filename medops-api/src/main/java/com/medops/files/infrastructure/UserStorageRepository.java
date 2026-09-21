package com.medops.files.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link UserStorage} with atomic quota reservation methods.
 *
 * <p>The quota is a single-column counter ({@code used_bytes}). Uploads
 * reserve bytes via a single atomic {@code UPDATE ... WHERE used_bytes + ? <= ?}
 * statement so concurrent uploads cannot both pass the check and push the total
 * over the limit. Releases subtract (clamped at zero) to handle the edge case
 * where a rollback or cleanup underestimates the stored size.
 */
public interface UserStorageRepository extends JpaRepository<UserStorage, UUID> {

    Optional<UserStorage> findByUserId(UUID userId);

    /**
     * Creates a zero-usage row for a user that does not yet have one.
     * Safe to call for every upload; a no-op when the row already exists.
     */
    @Modifying
    @Query(value = """
        INSERT INTO files.user_storage (user_id, used_bytes)
        VALUES (:userId, 0)
        ON CONFLICT (user_id) DO NOTHING
        """, nativeQuery = true)
    void ensureRecordExists(@Param("userId") UUID userId);

    /**
     * Atomically reserves {@code fileSize} bytes for the given user, but only
     * if the resulting total does not exceed {@code maxBytes}.
     *
     * @return the number of rows updated ({@code 1} if the reservation succeeded,
     *         {@code 0} if the quota would be exceeded)
     */
    @Modifying
    @Query(value = """
        UPDATE files.user_storage
        SET used_bytes = used_bytes + :fileSize
        WHERE user_id = :userId
          AND used_bytes + :fileSize <= :maxBytes
        """, nativeQuery = true)
    int reserveQuota(@Param("userId") UUID userId,
                     @Param("fileSize") long fileSize,
                     @Param("maxBytes") long maxBytes);

    /**
     * Releases a previously reserved allocation, clamping at zero.
     */
    @Modifying
    @Query(value = """
        UPDATE files.user_storage
        SET used_bytes = GREATEST(0, used_bytes - :fileSize)
        WHERE user_id = :userId
        """, nativeQuery = true)
    void releaseQuota(@Param("userId") UUID userId, @Param("fileSize") long fileSize);
}
