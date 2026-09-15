package com.medops.auth.security.codec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Shared cryptographic helpers for the password-reset flow: secure 6-digit OTP
 * generation, HMAC-SHA256 OTP hashing, and SHA-256 hashing of high-entropy reset
 * tokens. Centralised so the security-sensitive parts live in exactly one place
 * instead of being copy-pasted across the reset services.
 */
@Component
public class PasswordResetCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a {@code length}-digit numeric OTP using a cryptographically
     * secure RNG (never {@code ThreadLocalRandom}, which is not suitable for
     * security tokens).
     *
     * @param length number of digits (e.g. 6)
     * @return the OTP as a zero-prefixed digit string
     */
    public String generateOtp(int length) {
        if (length < 1 || length > 9) {
            throw new IllegalArgumentException("OTP length must be between 1 and 9");
        }
        int min = (int) Math.pow(10, length - 1);
        int max = (int) Math.pow(10, length) - 1;
        return String.format("%0" + length + "d", secureRandom.nextInt(min, max + 1));
    }

    /**
     * HMAC-SHA256 digest, Base64-encoded. Used so a stolen Redis dump does not
     * reveal OTPs (6 digits are trivially brute-forced when stored in plaintext).
     *
     * @param data   the plaintext OTP
     * @param secret the server-side HMAC secret
     * @return Base64 encoded HMAC-SHA256 digest
     */
    public String hmacSha256(String data, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("PASSWORD_RESET_OTP_HMAC_SECRET must be configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    /**
     * SHA-256 digest, Base64-encoded. Deterministic, so it doubles as the Redis
     * lookup key for high-entropy reset tokens (no need for a salted KDF here â€”
     * the token is random, not user-chosen).
     *
     * @param data the raw reset token
     * @return Base64 encoded SHA-256 digest
     */
    public String sha256(String data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Constant-time comparison of two pre-hashed values so OTP hash timing cannot
     * leak byte-level information about the stored digest.
     *
     * @param expected the stored hash
     * @param actual   the freshly computed hash
     * @return {@code true} when both byte arrays are equal
     */
    public boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
