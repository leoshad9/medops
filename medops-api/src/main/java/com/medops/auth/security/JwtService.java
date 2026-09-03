package com.medops.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

@Service
public final class JwtService {

    private final SecretKey key;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties jwtProperties) {
        try {
            this.key = Keys.hmacShaKeyFor(decodeSigningSecret(jwtProperties.secret()));
        } catch (WeakKeyException e) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must decode to at least 32 bytes. Generate with: openssl rand -base64 32",
                    e);
        }
        this.accessTokenExpiryMs = jwtProperties.accessTokenExpiryMs();
        this.refreshTokenExpiryMs = jwtProperties.refreshTokenExpiryMs();
    }

    /**
     * Accepts standard Base64 or Base64URL (the latter uses {@code -} and {@code _}).
     * Docs historically suggested {@code openssl rand -hex 32}; those hex strings are valid
     * standard Base64 and still decode here.
     */
    static byte[] decodeSigningSecret(String secret) {
        String trimmed = secret.trim();
        boolean urlAlphabet = trimmed.indexOf('-') >= 0 || trimmed.indexOf('_') >= 0;
        try {
            return urlAlphabet
                    ? Decoders.BASE64URL.decode(trimmed)
                    : Decoders.BASE64.decode(trimmed);
        } catch (DecodingException e) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must be Base64 or Base64URL of at least 32 random bytes."
                            + " Generate with: openssl rand -base64 32",
                    e);
        }
    }

    public String generateAccessToken(UserDetails userDetails) {
        Instant now = Instant.now();
        List<String> authorities = new ArrayList<>();
        for (GrantedAuthority authority : userDetails.getAuthorities()) {
            authorities.add(authority.getAuthority());
        }
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            return extractUsername(token).equals(userDetails.getUsername());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    /**
     * Generates a cryptographically random opaque refresh token. Unlike the access token,
     * this is not a JWT - it is stored server-side (hashed) so it can be looked up, rotated,
     * and revoked on demand.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a raw token for storage/lookup. SHA-256 (not BCrypt) is sufficient here because
     * the token is high-entropy random data rather than a user-chosen secret, so it doesn't
     * need a slow, salted KDF - and a fast deterministic hash allows direct DB lookup by hash.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
