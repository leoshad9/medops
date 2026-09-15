package com.medops.auth.domain;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * RefreshToken entity for JWT token persistence.
 * Supports token rotation, revocation, and multi-device session management.
 */
@Entity
@Table(name = "refresh_tokens", schema = "auth", indexes = {
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
        @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at"),
        @Index(name = "idx_refresh_tokens_revoked_at", columnList = "revoked_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class RefreshToken {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(nullable = false)
    @ToString.Include
    private ZonedDateTime expiresAt;

    @Column
    private ZonedDateTime revokedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public boolean isValid() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return now.isBefore(expiresAt) && revokedAt == null;
    }

    public void revoke() {
        this.revokedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
