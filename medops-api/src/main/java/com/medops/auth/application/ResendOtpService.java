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

    /**
     * Message shared by the expired-flow and unknown-flow branches so neither reveals
     * whether a reset flow ever existed.
     */
    static final String EXPIRED_MESSAGE = "This reset flow is no longer valid. Please request a new OTP.";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final MailDeliveryExecutor mailDeliveryExecutor;
    private final AuditService auditService;
    private final PasswordResetProperties properties;
    private final PasswordResetCodec codec;


    /**
     * Regenerates the OTP for an existing reset flow and queues it for delivery.
     *
     * <p>Responses are flow-state only ({@code SENT}/{@code COOLDOWN}/{@code EXPIRED}):
     * because forgot-password burns dummy flows for unknown emails, the status never
     * reveals whether an account exists.
     *
     * @param request the resend request containing the reset flow id
     * @return the resend outcome
     */
    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        String resetFlowId = request.resetFlowId();

        String otpJson = redisTemplate.opsForValue().get(PasswordResetKeys.OTP_PREFIX + resetFlowId);
        if (otpJson == null) {
            log.warn("Resend OTP attempted for non-existent or expired flow: {}", resetFlowId);
            return new ResendOtpResponse(Status.EXPIRED, EXPIRED_MESSAGE);
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
            // Dummy/unknown-email flow: an OTP exists so the resend behaves exactly like a
            // real one (same writes, same SENT response), but there is nobody to email and
            // nothing to audit - the response therefore cannot reveal account existence.
            log.info("OTP resend queued for delivery, flow: {}", resetFlowId);
            return new ResendOtpResponse(Status.SENT,
                    "If an account exists for this flow, an OTP has been resent.");
        }

        mailDeliveryExecutor.execute(() -> {
            try {
                emailService.sendOtpEmail(email, otp, (int) properties.otp().ttl().toMinutes());
            } catch (EmailSendingException e) {
                log.error("OTP resend failed for flow {} — dropping flow keys only if unchanged",
                        resetFlowId, e);
                deleteOtpKeysIfUnchanged(resetFlowId, resendKey, otpHash);
            }
        });

        auditService.recordEventBestEffort(AuditEventType.PASSWORD_RESET_REQUESTED, null, email);
        log.info("OTP resend queued for delivery, flow: {}", resetFlowId);
        return new ResendOtpResponse(Status.SENT,
                "If an account exists for this flow, an OTP has been resent.");
    }

    /**
     * Deletes a flow's OTP keys after an email failure, but only when the stored OTP
     * still matches the hash this task attempted to deliver.
     *
     * <p>A newer resend may have regenerated the OTP while this (older) delivery was
     * still in flight; deleting unconditionally would destroy the fresh code. The
     * comparison is constant-time so the stored hash cannot leak byte-level timing.
     *
     * @param resetFlowId the flow id owning the keys
     * @param resendKey the cooldown sentinel key for this resend attempt
     * @param attemptedOtpHash the OTP hash this task tried to deliver
     */
    private void deleteOtpKeysIfUnchanged(String resetFlowId, String resendKey, String attemptedOtpHash) {
        try {
            String currentJson = redisTemplate.opsForValue().get(PasswordResetKeys.OTP_PREFIX + resetFlowId);
            if (currentJson == null) {
                return;
            }
            PasswordResetOtp current = objectMapper.readValue(currentJson, PasswordResetOtp.class);
            if (!codec.constantTimeEquals(current.otpHash(), attemptedOtpHash)) {
                // A newer OTP superseded this one; leave the fresh flow untouched and only
                // release this attempt's cooldown so the user can retry immediately.
                log.info("OTP resend cleanup skipped for flow: {}; a newer OTP exists", resetFlowId);
            } else {
                redisTemplate.delete(PasswordResetKeys.OTP_PREFIX + resetFlowId);
                redisTemplate.delete(PasswordResetKeys.USER_PREFIX + resetFlowId);
                redisTemplate.delete(PasswordResetKeys.USER_ID_PREFIX + resetFlowId);
            }
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("OTP resend cleanup failed for flow: {}", resetFlowId, e);
        } finally {
            try {
                redisTemplate.delete(resendKey);
            } catch (RuntimeException e) {
                log.error("Failed to release resend cooldown for flow: {}", resetFlowId, e);
            }
        }
    }
}
