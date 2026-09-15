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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request, String resetToken) {
        String tokenHash = codec.sha256(resetToken);

        String tokenJson = redisTemplate.opsForValue().get(PasswordResetKeys.TOKEN_PREFIX + tokenHash);
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

        try {
            redisTemplate.delete(PasswordResetKeys.TOKEN_PREFIX + tokenHash);
        } catch (RuntimeException e) {
            log.error("Failed to delete reset token {} after successful password reset; "
                    + "token will expire naturally", tokenHash, e);
        }

        refreshTokenRepository.revokeAllUserTokens(user.getId(), ZonedDateTime.now(ZoneOffset.UTC));

        auditService.recordEventBestEffort(AuditEventType.PASSWORD_RESET_SUCCESS, user.getId(), user.getEmail());
        log.info("Password reset successful for user: {}", user.getId());

        sendConfirmationEmail(user.getEmail());
        return new ResetPasswordResponse("Password reset successfully. Please log in again.");
    }

    private void sendConfirmationEmail(String email) {
        mailDeliveryExecutor.execute(() -> {
            try {
                emailService.sendPasswordResetConfirmationEmail(email);
            } catch (EmailSendingException e) {
                log.error("Password reset succeeded for {} but the confirmation email could not be sent", email, e);
            }
        });
    }
}
