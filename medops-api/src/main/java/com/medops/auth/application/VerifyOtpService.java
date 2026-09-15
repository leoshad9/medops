package com.medops.auth.application;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.domain.PasswordResetOtp;
import com.medops.auth.domain.PasswordResetToken;
import com.medops.auth.dto.passwordreset.VerifyOtpRequest;
import com.medops.auth.dto.passwordreset.VerifyOtpResponse;
import com.medops.auth.security.PasswordResetKeys;
import com.medops.auth.security.PasswordResetProperties;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpService {

    private static final String INVALID_OR_EXPIRED = "Invalid or expired OTP.";

    @SuppressWarnings("unchecked") // Class literals cannot express List<String>; Redis MULTI elements are Strings
    private static final DefaultRedisScript<List<String>> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            """
            local val = redis.call('GET', KEYS[1])
            if val then
              local ttl = redis.call('TTL', KEYS[1])
              redis.call('DEL', KEYS[1])
              return {val, tostring(ttl)}
            end
            return nil""",
            (Class<List<String>>) (Class<?>) List.class);

    /**
     * Versioned compare-and-set restore for an OTP record: only writes the replacement
     * JSON when the key currently holds exactly the claimed JSON (or is absent because
     * the claim script just deleted it). A stale or late writer therefore can never
     * clobber a newer OTP resend, and two concurrent failures cannot both double-count.
     *
     * <p>Returns 1 when the value was written, 0 otherwise.
     */
    @SuppressWarnings("unchecked") // Class literals cannot express Long; Lua returns an integer
    private static final DefaultRedisScript<Long> COMPARE_AND_RESTORE_SCRIPT = new DefaultRedisScript<>(
            """
            local cur = redis.call('GET', KEYS[1])
            if (not cur) or (cur == ARGV[1]) then
              redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
              return 1
            end
            return 0""",
            (Class<Long>) (Class<?>) Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final PasswordResetProperties properties;
    private final PasswordResetCodec codec;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String resetFlowId = request.resetFlowId();
        String otpKey = PasswordResetKeys.OTP_PREFIX + resetFlowId;

        List<String> scriptResult;
        try {
            // Atomically claim the OTP record and its exact TTL (so two concurrent requests
            // can never both redeem the same code, nor can a resend interleave and mess up
            // the TTL for a failed-attempt restore).
            // Bind the RedisScript's result type explicitly to List<String> so the
            // compiler resolves the generic execute(RedisScript<T>, List<K>, Object...)
            // overload without an unchecked-assignment warning.
            @SuppressWarnings("unchecked")
            RedisScript<List<String>> getAndDeleteScript =
                    (RedisScript<List<String>>) (RedisScript<?>) GET_AND_DELETE_SCRIPT;
            scriptResult = redisTemplate.execute(
                    getAndDeleteScript, Collections.singletonList(otpKey));
        } catch (DataAccessException e) {
            // Redis is down mid-claim: the OTP may or may not have been consumed server-side,
            // so never mutate blindly. Return a recoverable error - the user can retry, or the
            // OTP/record will simply expire and they can request a fresh one.
            log.error("Redis unavailable while claiming OTP for flow: {}; user can retry", resetFlowId, e);
            return new VerifyOtpResponse(
                    "Verification service is temporarily unavailable. Please try again, "
                            + "or request a new OTP if this persists.",
                    null);
        }

        if (scriptResult == null || scriptResult.size() != 2) {
            log.warn("OTP verification attempted for non-existent or expired flow: {}", resetFlowId);
            return new VerifyOtpResponse(INVALID_OR_EXPIRED, null);
        }

        String otpJson = scriptResult.get(0);
        long otpTtlSeconds;
        try {
            otpTtlSeconds = Long.parseLong(scriptResult.get(1));
        } catch (NumberFormatException e) {
            otpTtlSeconds = 0;
        }

        if (otpTtlSeconds <= 0) {
            log.warn("OTP record for flow {} had no remaining TTL; treating as expired", resetFlowId);
            deleteFlowKeys(resetFlowId);
            return new VerifyOtpResponse(INVALID_OR_EXPIRED, null);
        }

        PasswordResetOtp otpRecord;
        try {
            otpRecord = objectMapper.readValue(otpJson, PasswordResetOtp.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize OTP record for flow: {}", resetFlowId, e);
            deleteFlowKeys(resetFlowId);
            return new VerifyOtpResponse(INVALID_OR_EXPIRED, null);
        }

        if (otpRecord.attempts() >= properties.otp().maxAttempts()) {
            log.warn("Max OTP attempts exceeded for flow: {}", resetFlowId);
            deleteFlowKeys(resetFlowId);
            return new VerifyOtpResponse("Too many failed attempts. Please request a new OTP.", null);
        }

        String submittedOtpHash = codec.hmacSha256(request.otp(), properties.otp().hmacSecret());
        if (!codec.constantTimeEquals(submittedOtpHash, otpRecord.otpHash())) {
            recordFailedAttempt(resetFlowId, otpRecord, otpJson, otpTtlSeconds);
            String email = redisTemplate.opsForValue().get(PasswordResetKeys.USER_PREFIX + resetFlowId);
            auditService.recordEventBestEffort(AuditEventType.PASSWORD_RESET_OTP_FAILED, null, email);
            log.warn("Invalid OTP submitted for flow: {}", resetFlowId);
            return new VerifyOtpResponse("Invalid OTP.", null);
        }

        String email = redisTemplate.opsForValue().get(PasswordResetKeys.USER_PREFIX + resetFlowId);
        String userIdRaw = redisTemplate.opsForValue().get(PasswordResetKeys.USER_ID_PREFIX + resetFlowId);
        if (email == null || userIdRaw == null) {
            log.error("Incomplete flow state for reset flow: {}", resetFlowId);
            deleteFlowKeys(resetFlowId);
            return new VerifyOtpResponse(INVALID_OR_EXPIRED, null);
        }

        String resetToken = generateResetToken();
        String tokenHash = codec.sha256(resetToken);
        PasswordResetToken tokenRecord = new PasswordResetToken(UUID.fromString(userIdRaw), resetFlowId, false);

        try {
            String tokenJson = objectMapper.writeValueAsString(tokenRecord);
            redisTemplate.opsForValue().set(
                    PasswordResetKeys.TOKEN_PREFIX + tokenHash, tokenJson, properties.resetToken().ttl());
        } catch (JsonProcessingException e) {
            // Fail-closed isn't right here: the OTP has already been atomically
            // consumed (script), so throwing would lock the user out with no way
            // to retry. Restore the OTP record (preserving remaining TTL) and
            // rethrow — the caller sees a transient failure and can retry.
            log.error("Failed to store reset token for flow: {}; restoring OTP", resetFlowId, e);
            try {
                restoreOtpIfPresent(resetFlowId, otpRecord, otpJson, otpTtlSeconds);
            } catch (Exception restoreEx) {
                log.error("Also failed to restore OTP for flow: {}; user may be locked out", resetFlowId, restoreEx);
            }
            throw new IllegalStateException("Failed to store reset token", e);
        }

        deleteFlowKeys(resetFlowId);
        auditService.recordEventBestEffort(AuditEventType.PASSWORD_RESET_OTP_VERIFIED, null, email);
        log.info("OTP verified successfully for flow: {}", resetFlowId);
        return new VerifyOtpResponse("OTP verified successfully.", resetToken);
    }

    /**
     * Re-stores the OTP record with one more failed attempt, preserving the
     * original remaining TTL.
     *
     * <p>Uses a versioned compare-and-set (the claimed JSON is the expected value) so
     * two concurrent failures cannot both restore from the same base and lose one
     * increment, and a late writer cannot clobber a freshly resent OTP.
     *
     * @param resetFlowId the flow id owning the OTP record
     * @param otpRecord the claimed OTP record (base for the increment)
     * @param claimedJson the exact JSON that was claimed, used as the CAS expected value
     * @param remainingTtlSeconds the TTL to re-apply to the restored record
     */
    private void recordFailedAttempt(
            String resetFlowId, PasswordResetOtp otpRecord, String claimedJson, long remainingTtlSeconds) {
        try {
            String updatedJson = objectMapper.writeValueAsString(otpRecord.incrementAttempts());
            Long written = redisTemplate.execute(
                    COMPARE_AND_RESTORE_SCRIPT,
                    Collections.singletonList(PasswordResetKeys.OTP_PREFIX + resetFlowId),
                    claimedJson, updatedJson, String.valueOf(remainingTtlSeconds));
            if (!Long.valueOf(1L).equals(written)) {
                // Someone else already restored or resent; do not fight them - count the
                // attempt conservatively by leaving their record in place.
                log.warn("OTP restore skipped for flow: {}; record changed concurrently", resetFlowId);
            }
        } catch (JsonProcessingException e) {
            // Failing closed beats an uncounted attempt: without the increment a
            // brute-forcer would never hit the max-attempts cap.
            log.error("Failed to record OTP attempt for flow: {}; invalidating flow", resetFlowId, e);
            deleteFlowKeys(resetFlowId);
        }
    }

    private void deleteFlowKeys(String resetFlowId) {
        redisTemplate.delete(PasswordResetKeys.OTP_PREFIX + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.USER_PREFIX + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.USER_ID_PREFIX + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.RESEND_PREFIX + resetFlowId);
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Restores a previously claimed OTP record without incrementing attempts.
     *
     * <p>Also compare-and-set guarded: only restores when the key is still absent or
     * still holds the claimed JSON, so a concurrent resend is never overwritten.
     *
     * @param resetFlowId the flow id owning the OTP record
     * @param otpRecord the claimed OTP record to restore
     * @param claimedJson the exact JSON that was claimed, used as the CAS expected value
     * @param remainingTtlSeconds the TTL to re-apply to the restored record
     */
    private void restoreOtpIfPresent(
            String resetFlowId, PasswordResetOtp otpRecord, String claimedJson, long remainingTtlSeconds) {
        try {
            String updatedJson = objectMapper.writeValueAsString(otpRecord);
            redisTemplate.execute(
                    COMPARE_AND_RESTORE_SCRIPT,
                    Collections.singletonList(PasswordResetKeys.OTP_PREFIX + resetFlowId),
                    claimedJson, updatedJson, String.valueOf(remainingTtlSeconds));
        } catch (JsonProcessingException e) {
            log.error("Failed to restore OTP for flow: {}", resetFlowId, e);
        }
    }
}
