package com.medops.auth.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.security.PasswordResetProperties;
import com.medops.auth.dto.passwordreset.ForgotPasswordRequest;
import com.medops.auth.dto.passwordreset.ForgotPasswordResponse;
import com.medops.auth.domain.User;
import com.medops.auth.domain.UserStatus;
import com.medops.auth.infrastructure.email.EmailService;
import com.medops.auth.infrastructure.email.MailDeliveryExecutor;
import com.medops.auth.infrastructure.repository.UserRepository;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ForgotPasswordService}. Pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ForgotPasswordServiceTest {

    private static final String EMAIL = "patient@medops.dev";
    private static final String CLIENT_IP = "192.0.2.1";

    private static final PasswordResetProperties PROPERTIES = new PasswordResetProperties(
            new PasswordResetProperties.Otp(Duration.ofMinutes(10), 5, 6, "test-hmac-secret"),
            new PasswordResetProperties.ResetToken(Duration.ofMinutes(15)),
            new PasswordResetProperties.Resend(Duration.ofSeconds(60)),
            new PasswordResetProperties.RateLimit(3, 10));

    @Mock
    private UserRepository userRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;
    @Mock
    private RateLimiterStore rateLimiterStore;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordResetCodec codec = new PasswordResetCodec();

    private ForgotPasswordService service;

    @BeforeEach
    void setUp() {
        // redisTemplate.opsForValue() is stubbed per-test: most flows short-circuit
        // before touching Redis and strict stubs reject an unused stub.
        service = new ForgotPasswordService(userRepository, redisTemplate, objectMapper,
                emailService, new MailDeliveryExecutor(Runnable::run), auditService,
                rateLimiterStore, PROPERTIES, codec);
    }

    @Test
    void forgotPassword_storesOtpSendsEmailAndAudits_whenAccountExists() {
        User user = userWithId(EMAIL);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL), CLIENT_IP);

        assertThat(response.resetFlowId()).isNotNull();
        assertThat(response.message()).contains("OTP");
        verify(valueOperations).set(startsWith("password-reset:otp:"), anyString(), eq(Duration.ofMinutes(10)));
        verify(valueOperations).set(startsWith("password-reset:flow:user:"), eq(EMAIL), any());
        verify(valueOperations).set(startsWith("password-reset:flow:user:id:"), eq(user.getId().toString()), any());
        verify(emailService).sendOtpEmail(eq(EMAIL), anyString(), eq(10));
        verify(auditService).recordEventBestEffort(AuditEventType.PASSWORD_RESET_REQUESTED, user.getId(), EMAIL);
    }

    @Test
    void forgotPassword_isGenericResponse_whenAccountDoesNotExist() {
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        ForgotPasswordResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL), CLIENT_IP);

        assertThat(response.resetFlowId()).isNotNull();
        assertThat(response.message()).contains("OTP");
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
        verify(auditService, never()).recordEventBestEffort(any(AuditEventType.class), any(), any());
    }

    @Test
    void forgotPassword_returnsNoFlowId_whenEmailRateLimited() {
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any())).thenReturn(false);

        ForgotPasswordResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL), CLIENT_IP);

        assertThat(response.resetFlowId()).isNull();
        verify(userRepository, never()).findByEmail(anyString());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
    }

    private static User userWithId(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("encoded-password")
                .status(UserStatus.ACTIVE)
                .build();
    }
}
