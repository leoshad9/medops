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


    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request, String clientIp) {
        String email = normalizeEmail(request.email());

        if (!rateLimiterStore.tryAcquire(RATE_LIMIT_EMAIL_PREFIX + email,
                properties.rateLimit().forgotPerEmailPerHour(), Duration.ofHours(1))) {
            log.warn("Rate limit exceeded for email: {}", email);
            return new ForgotPasswordResponse(GENERIC_MESSAGE, null);
        }

        if (!rateLimiterStore.tryAcquire(RATE_LIMIT_IP_PREFIX + clientIp,
                properties.rateLimit().forgotPerIpPerMinute(), Duration.ofMinutes(1))) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return new ForgotPasswordResponse(GENERIC_MESSAGE, null);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        String resetFlowId = UUID.randomUUID().toString();

        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email");
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

    private void deleteFlowKeys(String resetFlowId) {
        redisTemplate.delete(PasswordResetKeys.OTP_PREFIX    + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.USER_PREFIX   + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.USER_ID_PREFIX + resetFlowId);
        redisTemplate.delete(PasswordResetKeys.RESEND_PREFIX + resetFlowId);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
