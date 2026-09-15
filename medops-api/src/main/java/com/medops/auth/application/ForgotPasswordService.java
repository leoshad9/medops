package com.medops.auth.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.security.PasswordResetProperties;
import com.medops.auth.domain.PasswordResetOtp;
import com.medops.auth.dto.passwordreset.ForgotPasswordRequest;
import com.medops.auth.dto.passwordreset.ForgotPasswordResponse;
import com.medops.auth.domain.User;
import com.medops.auth.infrastructure.email.EmailSendingException;
import com.medops.auth.infrastructure.email.EmailService;
import com.medops.auth.infrastructure.email.MailDeliveryExecutor;
import com.medops.auth.infrastructure.repository.UserRepository;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.auth.security.PasswordResetKeys;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.util.LogMasking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    private static final String RATE_LIMIT_EMAIL_PREFIX = "pwd-reset:email:";
    private static final String RATE_LIMIT_IP_PREFIX    = "pwd-reset:ip:";
    private static final String GENERIC_MESSAGE =
            "If an account exists for this email, an OTP has been sent.";
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final MailDeliveryExecutor mailDeliveryExecutor;
    private final AuditService auditService;
    private final RateLimiterStore rateLimiterStore;
    private final PasswordResetProperties properties;
    private final PasswordResetCodec codec;


    /**
     * Starts a password-reset flow for the given email address.
     *
     * <p>Always returns the same generic message and a {@code resetFlowId}, whether or
     * not an account exists, so the response cannot be used to enumerate accounts.
     * Unknown emails still burn a dummy OTP flow (same Redis writes, no email sent) to
     * equalise timing and downstream resend/verify behaviour.
     *
     * @param request the forgot-password request containing the email
     * @param clientIp the caller IP used for rate limiting (never logged in the clear)
     * @return generic message plus a reset flow id, or a null flow id when rate limited
     */
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request, String clientIp) {
        String email = normalizeEmail(request.email());

        if (!rateLimiterStore.tryAcquire(RATE_LIMIT_EMAIL_PREFIX + email,
                properties.rateLimit().forgotPerEmailPerHour(), Duration.ofHours(1))) {
            log.warn("Password-reset rate limit exceeded for email: {}", LogMasking.maskEmail(email));
            return new ForgotPasswordResponse(GENERIC_MESSAGE, null);
        }

        if (!rateLimiterStore.tryAcquire(RATE_LIMIT_IP_PREFIX + clientIp,
                properties.rateLimit().forgotPerIpPerMinute(), Duration.ofMinutes(1))) {
            log.warn("Password-reset rate limit exceeded for IP: {}", LogMasking.maskIp(clientIp));
            return new ForgotPasswordResponse(GENERIC_MESSAGE, null);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        String resetFlowId = UUID.randomUUID().toString();

        if (userOpt.isEmpty()) {
            // Anti-enumeration: do the same expensive work as the known-account path
            // (OTP generation, HMAC, Redis writes) and return the same shape, but never
            // send an email or write an audit event. The dummy OTP hash is random so it
            // can never be guessed; verify/resend treat it exactly like a real flow.
            String dummyOtp = codec.generateOtp(properties.otp().length());
            String dummyHash = codec.hmacSha256(dummyOtp, properties.otp().hmacSecret());
            storeDummyFlow(resetFlowId, dummyHash);
            log.info("Password reset OTP requested");
            return new ForgotPasswordResponse(GENERIC_MESSAGE, resetFlowId);
        }

        User user = userOpt.get();
        String otp = codec.generateOtp(properties.otp().length());
        String otpHash = codec.hmacSha256(otp, properties.otp().hmacSecret());
        storeFlow(resetFlowId, user, new PasswordResetOtp(otpHash, 0));

        mailDeliveryExecutor.execute(() -> {
            try {
                emailService.sendOtpEmail(user.getEmail(), otp, (int) properties.otp().ttl().toMinutes());
            } catch (EmailSendingException e) {
                log.error("OTP email delivery failed for flow {} — deleting flow keys", resetFlowId, e);
                deleteFlowKeys(resetFlowId);
            }
        });

        auditService.recordEventBestEffort(AuditEventType.PASSWORD_RESET_REQUESTED, user.getId(), user.getEmail());
        log.info("Password reset OTP queued for delivery, user: {}", user.getId());
        return new ForgotPasswordResponse(GENERIC_MESSAGE, resetFlowId);
    }

    /**
     * Stores a dummy OTP flow for an unknown email so timing and downstream
     * resend/verify responses are indistinguishable from a real flow.
     *
     * @param resetFlowId the flow id to store under
     * @param otpHash the HMAC of a random, never-delivered OTP
     */
    private void storeDummyFlow(String resetFlowId, String otpHash) {
        List<String> keysWritten = new ArrayList<>();
        try {
            String json = objectMapper.writeValueAsString(new PasswordResetOtp(otpHash, 0));
            redisTemplate.opsForValue().set(PasswordResetKeys.OTP_PREFIX + resetFlowId, json,
                    properties.otp().ttl());
            keysWritten.add(PasswordResetKeys.OTP_PREFIX + resetFlowId);
        } catch (JsonProcessingException | RuntimeException e) {
            keysWritten.forEach(k -> {
                try {
                    redisTemplate.delete(k);
                } catch (Exception ignored) {
                    // best-effort cleanup of partially written keys
                }
            });
            log.error("Failed to store dummy OTP flow {}; cleaned up {} partial key(s)",
                    resetFlowId, keysWritten.size(), e);
            throw new IllegalStateException("Failed to store OTP", e);
        }
    }

    /**
     * Persists the OTP plus flow lookups for a real account.
     *
     * @param resetFlowId the flow id to store under
     * @param user the account owner
     * @param otpRecord the OTP hash plus attempt counter
     */
    private void storeFlow(String resetFlowId, User user, PasswordResetOtp otpRecord) {
        List<String> keysWritten = new ArrayList<>();
        try {
            String json = objectMapper.writeValueAsString(otpRecord);
            redisTemplate.opsForValue().set(PasswordResetKeys.OTP_PREFIX + resetFlowId, json,
                    properties.otp().ttl());
            keysWritten.add(PasswordResetKeys.OTP_PREFIX + resetFlowId);
            redisTemplate.opsForValue().set(PasswordResetKeys.USER_PREFIX + resetFlowId, user.getEmail(),
                    properties.otp().ttl());
            keysWritten.add(PasswordResetKeys.USER_PREFIX + resetFlowId);
            redisTemplate.opsForValue().set(PasswordResetKeys.USER_ID_PREFIX + resetFlowId,
                    user.getId().toString(), properties.otp().ttl());
            keysWritten.add(PasswordResetKeys.USER_ID_PREFIX + resetFlowId);
        } catch (JsonProcessingException | RuntimeException e) {
            keysWritten.forEach(k -> {
                try {
                    redisTemplate.delete(k);
                } catch (Exception ignored) {
                    // best-effort cleanup of partially written keys
                }
            });
            log.error("Failed to store OTP flow {}; cleaned up {} partial key(s)", resetFlowId, keysWritten.size(), e);
            throw new IllegalStateException("Failed to store OTP", e);
        }
    }

    /**
     * Deletes every Redis key belonging to a reset flow.
     *
     * @param resetFlowId the flow id whose keys should be removed
     */
    private void deleteFlowKeys(String resetFlowId) {
        redisTemplate.delete(PasswordResetKeys.OTP_PREFIX    + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.USER_PREFIX   + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.USER_ID_PREFIX + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.RESEND_PREFIX + resetFlowId);
    }

    /**
     * Normalises an email address for lookup and rate-limit keys.
     *
     * @param email the raw request email
     * @return trimmed, lower-cased email
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
