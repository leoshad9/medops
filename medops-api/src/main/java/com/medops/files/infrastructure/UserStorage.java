package com.medops.files.infrastructure;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Tracks per-user storage consumption for clinical file uploads (PDFs).
 *
 * <p>Each patient has one row; {@code usedBytes} is incremented atomically on
 * upload (via {@link UserStorageRepository#reserveQuota}) and decremented when
 * a clinical report is removed. Keeps the Docker volume that backs
 * {@link LocalClinicalFileStorage} from being exhausted by a single user.
 */
@Entity
@Table(name = "user_storage", schema = "files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class UserStorage {

    @Id
    @Column(name = "user_id", columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID userId;

    @Column(name = "used_bytes", nullable = false)
    private long usedBytes;
}
