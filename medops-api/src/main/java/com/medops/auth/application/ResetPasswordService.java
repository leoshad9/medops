package com.medops.auth.application;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.domain.PasswordResetToken;
import com.medops.auth.domain.User;
import com.medops.auth.dto.passwordreset.ResetPasswordRequest;
import com.medops.auth.dto.passwordreset.ResetPasswordResponse;
import com.medops.auth.infrastructure.email.EmailSendingException;
import com.medops.auth.infrastructure.email.EmailService;
import com.medops.auth.infrastructure.email.MailDeliveryExecutor;
import com.medops.auth.infrastructure.repository.RefreshTokenRepository;
import com.medops.auth.infrastructure.repository.UserRepository;
import com.medops.auth.security.PasswordResetKeys;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.util.LogMasking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finalises a password reset by consuming a single-use reset token.
 *
 * <p>Reset tokens are claimed atomically with Redis {@code GETDEL} so two
 * concurrent requests presenting the same token can never both succeed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordService {

    private static final String INVALID_OR_EXPIRED = "Invalid or expired reset token.";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MailDeliveryExecutor mailDeliveryExecutor;
    private final AuditService auditService;
    private final PasswordResetCodec codec;

    /**
     * Consumes a single-use reset token and sets the new password.
     *
     * @param request the new password payload
     * @param resetToken the raw reset token presented via HttpOnly cookie
     * @return a user-facing result; failures always return a generic message
     */
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request, String resetToken) {
        String tokenHash = codec.sha256(resetToken);
        String tokenKey = PasswordResetKeys.TOKEN_PREFIX + tokenHash;

        // Atomically claim the token (GETDEL): the value is returned and deleted in one
        // Redis operation, so two concurrent requests presenting the same token can never
        // both redeem it.
        String tokenJson = redisTemplate.opsForValue().getAndDelete(tokenKey);
        if (tokenJson == null) {
            log.warn("Password reset attempted with invalid/expired token");
            return new ResetPasswordResponse(INVALID_OR_EXPIRED);
        }

        PasswordResetToken tokenRecord;
        try {
            tokenRecord = objectMapper.readValue(tokenJson, PasswordResetToken.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize reset token record", e);
            return new ResetPasswordResponse(INVALID_OR_EXPIRED);
        }

        if (tokenRecord.used()) {
            log.warn("Password reset attempted with already used token");
            return new ResetPasswordResponse(INVALID_OR_EXPIRED);
        }

        Optional<User> userOpt = userRepository.findById(tokenRecord.userId());
        if (userOpt.isEmpty()) {
            log.warn("User not found for reset token: {}", tokenRecord.userId());
            return new ResetPasswordResponse(INVALID_OR_EXPIRED);
        }

        User user = userOpt.get();

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // The token was already consumed by GETDEL above, so there is nothing left to delete here.
        refreshTokenRepository.revokeAllUserTokens(user.getId(), ZonedDateTime.now(ZoneOffset.UTC));

        auditService.recordEventBestEffort(AuditEventType.PASSWORD_RESET_SUCCESS, user.getId(), user.getEmail());
        log.info("Password reset successful for user: {}", user.getId());

        sendConfirmationEmail(user.getEmail());
        return new ResetPasswordResponse("Password reset successfully. Please log in again.");
    }

    /**
     * Sends the post-reset confirmation email off the request thread.
     *
     * @param email the recipient address (never logged in the clear)
     */
    private void sendConfirmationEmail(String email) {
        mailDeliveryExecutor.execute(() -> {
            try {
                emailService.sendPasswordResetConfirmationEmail(email);
            } catch (EmailSendingException e) {
                log.error("Password reset succeeded for {} but the confirmation email could not be sent",
                        LogMasking.maskEmail(email), e);
            }
        });
    }
}
