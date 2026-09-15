package com.medops.auth.application;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.security.PasswordResetProperties;
import com.medops.auth.domain.PasswordResetOtp;
import com.medops.auth.dto.passwordreset.ResendOtpRequest;
import com.medops.auth.dto.passwordreset.ResendOtpResponse;
import com.medops.auth.dto.passwordreset.ResendOtpResponse.Status;
import com.medops.auth.infrastructure.email.EmailSendingException;
import com.medops.auth.infrastructure.email.EmailService;
import com.medops.auth.infrastructure.email.MailDeliveryExecutor;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.auth.security.PasswordResetKeys;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendOtpService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final MailDeliveryExecutor mailDeliveryExecutor;
    private final AuditService auditService;
    private final PasswordResetProperties properties;
    private final PasswordResetCodec codec;


    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        String resetFlowId = request.resetFlowId();

        String otpJson = redisTemplate.opsForValue().get(PasswordResetKeys.OTP_PREFIX + resetFlowId);
        if (otpJson == null) {
            log.warn("Resend OTP attempted for non-existent or expired flow: {}", resetFlowId);
            return new ResendOtpResponse(Status.EXPIRED,
                    "This reset flow is no longer valid. Please request a new OTP.");
        }

        String resendKey = PasswordResetKeys.RESEND_PREFIX + resetFlowId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(resendKey, "1", properties.resend().cooldown());
        if (!Boolean.TRUE.equals(acquired)) {
            Long remainingSeconds = redisTemplate.getExpire(resendKey, TimeUnit.SECONDS);
            long remaining = remainingSeconds != null && remainingSeconds > 0 ? remainingSeconds : 1;
            log.warn("Resend OTP attempted during cooldown for flow: {}", resetFlowId);
            return new ResendOtpResponse(Status.COOLDOWN,
                    "A new OTP is already being sent. "
                            + "Please wait %d seconds before trying again.".formatted(remaining));
        }

        int previousAttempts = 0;
        try {
            PasswordResetOtp oldOtp = objectMapper.readValue(otpJson, PasswordResetOtp.class);
            previousAttempts = oldOtp.attempts();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse previous OTP record to carry over attempts; resetting to 0", e);
        }

        String otp = codec.generateOtp(properties.otp().length());
        String otpHash = codec.hmacSha256(otp, properties.otp().hmacSecret());

        try {
            String json = objectMapper.writeValueAsString(new PasswordResetOtp(otpHash, previousAttempts));
            redisTemplate.opsForValue().set(PasswordResetKeys.OTP_PREFIX + resetFlowId, json, properties.otp().ttl());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OTP record for flow: {}", resetFlowId, e);
            throw new IllegalStateException("Failed to store OTP", e);
        }

        String email = redisTemplate.opsForValue().get(PasswordResetKeys.USER_PREFIX + resetFlowId);
        if (email == null) {
            log.warn("Flow {} has no stored email; OTP regenerated but not deliverable", resetFlowId);
            return new ResendOtpResponse(Status.SENT, "OTP regenerated but no email address on file.");
        }

        mailDeliveryExecutor.execute(() -> {
            try {
                emailService.sendOtpEmail(email, otp, (int) properties.otp().ttl().toMinutes());
            } catch (EmailSendingException e) {
                log.error("OTP resend failed for flow {} — dropping flow keys", resetFlowId, e);
                redisTemplate.delete(PasswordResetKeys.OTP_PREFIX + resetFlowId);
                redisTemplate.delete(PasswordResetKeys.USER_PREFIX + resetFlowId);
                redisTemplate.delete(PasswordResetKeys.USER_ID_PREFIX + resetFlowId);
                redisTemplate.delete(resendKey);
            }
        });

        auditService.recordEventBestEffort(AuditEventType.PASSWORD_RESET_REQUESTED, null, email);
        log.info("OTP resend queued for delivery, flow: {}", resetFlowId);
        return new ResendOtpResponse(Status.SENT,
                "If an account exists for this flow, an OTP has been resent.");
    }
}
