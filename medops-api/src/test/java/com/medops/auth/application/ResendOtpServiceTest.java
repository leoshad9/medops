package com.medops.auth.application;

import java.time.Duration;
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
import com.medops.auth.domain.PasswordResetOtp;
import com.medops.auth.dto.passwordreset.ResendOtpRequest;
import com.medops.auth.infrastructure.email.EmailService;
import com.medops.auth.infrastructure.email.MailDeliveryExecutor;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResendOtpService}. Pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ResendOtpServiceTest {

    private static final String FLOW_ID = UUID.randomUUID().toString();
    private static final String EMAIL = "patient@medops.dev";
    private static final String OTP_KEY = "password-reset:otp:" + FLOW_ID;
    private static final String RESEND_KEY = "password-reset:resend:" + FLOW_ID;
    private static final String FLOW_USER_KEY = "password-reset:flow:user:" + FLOW_ID;

    private static final PasswordResetProperties PROPERTIES = new PasswordResetProperties(
            new PasswordResetProperties.Otp(Duration.ofMinutes(10), 5, 6, "test-hmac-secret"),
            new PasswordResetProperties.ResetToken(Duration.ofMinutes(15)),
            new PasswordResetProperties.Resend(Duration.ofSeconds(60)),
            new PasswordResetProperties.RateLimit(3, 10));

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordResetCodec codec = new PasswordResetCodec();

    private ResendOtpService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new ResendOtpService(redisTemplate, objectMapper, emailService,
                new MailDeliveryExecutor(Runnable::run), auditService, PROPERTIES, codec);
    }

    @Test
    void resendOtp_regeneratesOtpSendsEmailAndAudits_whenCooldownAvailable() throws Exception {
        String existingOtpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256("111111", "test-hmac-secret"), 0));
        when(valueOperations.get(OTP_KEY)).thenReturn(existingOtpJson);
        when(valueOperations.setIfAbsent(RESEND_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(valueOperations.get(FLOW_USER_KEY)).thenReturn(EMAIL);

        service.resendOtp(new ResendOtpRequest(FLOW_ID));

        verify(valueOperations).set(eq(OTP_KEY), anyString(), eq(Duration.ofMinutes(10)));
        verify(emailService).sendOtpEmail(eq(EMAIL), anyString(), eq(10));
        verify(auditService).recordEventBestEffort(AuditEventType.PASSWORD_RESET_REQUESTED, null, EMAIL);
    }

    @Test
    void resendOtp_isSkippedDuringCooldown() throws Exception {
        String existingOtpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256("111111", "test-hmac-secret"), 0));
        when(valueOperations.get(OTP_KEY)).thenReturn(existingOtpJson);
        when(valueOperations.setIfAbsent(RESEND_KEY, "1", Duration.ofSeconds(60))).thenReturn(false);

        service.resendOtp(new ResendOtpRequest(FLOW_ID));

        verify(valueOperations, never()).set(eq(OTP_KEY), anyString(), any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
        verify(auditService, never()).recordEventBestEffort(any(AuditEventType.class), any(), any());
    }

    @Test
    void resendOtp_isSkippedForUnknownFlow() {
        when(valueOperations.get(OTP_KEY)).thenReturn(null);

        service.resendOtp(new ResendOtpRequest(FLOW_ID));

        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any());
    }
}
