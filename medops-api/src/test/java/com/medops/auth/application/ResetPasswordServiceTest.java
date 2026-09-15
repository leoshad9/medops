package com.medops.auth.application;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.domain.PasswordResetToken;
import com.medops.auth.dto.passwordreset.ResetPasswordRequest;
import com.medops.auth.dto.passwordreset.ResetPasswordResponse;
import com.medops.auth.domain.User;
import com.medops.auth.domain.UserStatus;
import com.medops.auth.infrastructure.email.EmailService;
import com.medops.auth.infrastructure.email.MailDeliveryExecutor;
import com.medops.auth.infrastructure.repository.RefreshTokenRepository;
import com.medops.auth.infrastructure.repository.UserRepository;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResetPasswordService}. Pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ResetPasswordServiceTest {

    private static final String TOKEN = "raw-reset-token-v3";
    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final String EMAIL = "patient@medops.dev";
    private static final String FLOW_ID = UUID.randomUUID().toString();

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordResetCodec codec = new PasswordResetCodec();

    private ResetPasswordService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new ResetPasswordService(redisTemplate, objectMapper, userRepository,
                refreshTokenRepository, passwordEncoder, emailService,
                new MailDeliveryExecutor(Runnable::run), auditService, codec);
    }

    @Test
    void resetPassword_updatesPasswordRevokesTokensAudits_andConfirmsByEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = userWithId(userId);
        String tokenKey = "password-reset:token:" + codec.sha256(TOKEN);
        when(valueOperations.getAndDelete(tokenKey)).thenReturn(storedTokenJson(userId, false));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("encoded-new-password");

        ResetPasswordResponse response = service.resetPassword(new ResetPasswordRequest(NEW_PASSWORD), TOKEN);

        assertThat(response.message()).contains("reset successfully");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllUserTokens(eq(userId), any(ZonedDateTime.class));
        // The token is consumed atomically on read (GETDEL), not marked used and re-stored.
        verify(valueOperations).getAndDelete(tokenKey);
        verify(redisTemplate, never()).delete(tokenKey);
        verify(auditService).recordEventBestEffort(AuditEventType.PASSWORD_RESET_SUCCESS, userId, EMAIL);
        verify(emailService).sendPasswordResetConfirmationEmail(EMAIL);
    }

    @Test
    void resetPassword_rejectsAlreadyUsedToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String tokenKey = "password-reset:token:" + codec.sha256(TOKEN);
        when(valueOperations.getAndDelete(tokenKey)).thenReturn(storedTokenJson(userId, true));

        ResetPasswordResponse response = service.resetPassword(new ResetPasswordRequest(NEW_PASSWORD), TOKEN);

        assertThat(response.message()).contains("Invalid or expired");
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        verify(emailService, never()).sendPasswordResetConfirmationEmail(anyString());
    }

    @Test
    void resetPassword_rejectsUnknownToken() {
        String tokenKey = "password-reset:token:" + codec.sha256(TOKEN);
        when(valueOperations.getAndDelete(tokenKey)).thenReturn(null);

        ResetPasswordResponse response = service.resetPassword(new ResetPasswordRequest(NEW_PASSWORD), TOKEN);

        assertThat(response.message()).contains("Invalid or expired");
        verify(userRepository, never()).findById(any());
        verify(auditService, never()).recordEventBestEffort(any(AuditEventType.class), any(), any());
    }

    private String storedTokenJson(UUID userId, boolean used) throws Exception {
        return objectMapper.writeValueAsString(new PasswordResetToken(userId, FLOW_ID, used));
    }

    private static User userWithId(UUID userId) {
        return User.builder()
                .id(userId)
                .email(EMAIL)
                .passwordHash("encoded-old-password")
                .status(UserStatus.ACTIVE)
                .build();
    }
}
