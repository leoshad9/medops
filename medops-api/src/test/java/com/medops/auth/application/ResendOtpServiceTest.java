package com.medops.auth.application;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import com.medops.auth.dto.passwordreset.ResendOtpResponse;
import com.medops.auth.infrastructure.email.EmailSendingException;
import com.medops.auth.infrastructure.email.EmailService;
import com.medops.auth.infrastructure.email.MailDeliveryExecutor;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private static final String FLOW_USER_ID_KEY = "password-reset:flow:user:id:" + FLOW_ID;
    private static final String USER_ID = UUID.randomUUID().toString();

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

    /**
     * Lifecycle: instantiated manually - {@code PROPERTIES} and {@code codec}
     * are not mocks. The IDE flags this as "never used" - it is invoked by
     * JUnit's {@code @BeforeEach}, which static analysis does not always trace.
     */
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new ResendOtpService(redisTemplate, objectMapper, emailService,
                new MailDeliveryExecutor(Runnable::run), auditService, PROPERTIES, codec);
    }

    /** Verifies that resend otp regenerates otp sends email and audits when cooldown available. */
    @Test
    void resendOtp_regeneratesOtpSendsEmailAndAudits_whenCooldownAvailable() throws Exception {
        String existingOtpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256("111111", "test-hmac-secret"), 0));
        when(valueOperations.get(OTP_KEY)).thenReturn(existingOtpJson);
        when(valueOperations.setIfAbsent(RESEND_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(valueOperations.get(FLOW_USER_KEY)).thenReturn(EMAIL);
        when(valueOperations.get(FLOW_USER_ID_KEY)).thenReturn(USER_ID);

        service.resendOtp(new ResendOtpRequest(FLOW_ID));

        verify(valueOperations).set(eq(OTP_KEY), anyString(), eq(Duration.ofMinutes(10)));
        verify(emailService).sendOtpEmail(eq(EMAIL), anyString(), eq(10));
        verify(auditService).recordEventBestEffort(AuditEventType.PASSWORD_RESET_REQUESTED,
                UUID.fromString(USER_ID), EMAIL);
    }

    /** Verifies that resend otp is skipped during cooldown. */
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

    /** Verifies that resend otp is skipped for unknown flow. */
    @Test
    void resendOtp_isSkippedForUnknownFlow() {
        when(valueOperations.get(OTP_KEY)).thenReturn(null);

        ResendOtpResponse response = service.resendOtp(new ResendOtpRequest(FLOW_ID));

        assertThat(response.status()).isEqualTo(ResendOtpResponse.Status.EXPIRED);
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any());
    }

    /** Verifies that resend otp returns generic sent without email for dummy flow. */
    @Test
    void resendOtp_returnsGenericSent_withoutEmail_forDummyFlow() throws Exception {
        String existingOtpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256("111111", "test-hmac-secret"), 0));
        when(valueOperations.get(OTP_KEY)).thenReturn(existingOtpJson);
        when(valueOperations.setIfAbsent(RESEND_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(valueOperations.get(FLOW_USER_KEY)).thenReturn(null);

        ResendOtpResponse response = service.resendOtp(new ResendOtpRequest(FLOW_ID));

        // Indistinguishable from a real resend: same SENT shape, no email, no audit.
        assertThat(response.status()).isEqualTo(ResendOtpResponse.Status.SENT);
        verify(valueOperations).set(eq(OTP_KEY), anyString(), eq(Duration.ofMinutes(10)));
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
        verify(auditService, never()).recordEventBestEffort(any(AuditEventType.class), any(), any());
    }

    /** Verifies that resend otp email failure deletes flow keys when otp unchanged. */
    @Test
    void resendOtp_emailFailure_deletesFlowKeys_whenOtpUnchanged() throws Exception {
        String existingOtpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256("111111", "test-hmac-secret"), 0));
        AtomicReference<String> storedOtp = new AtomicReference<>(existingOtpJson);
        when(valueOperations.get(OTP_KEY)).thenAnswer(inv -> storedOtp.get());
        org.mockito.stubbing.Answer<?> storeAnswer = inv -> {
            storedOtp.set(inv.getArgument(1));
            return null;
        };
        org.mockito.Mockito.doAnswer(storeAnswer).when(valueOperations)
                .set(eq(OTP_KEY), anyString(), eq(Duration.ofMinutes(10)));
        when(valueOperations.setIfAbsent(RESEND_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(valueOperations.get(FLOW_USER_KEY)).thenReturn(EMAIL);
        doThrow(new EmailSendingException("down", new RuntimeException("smtp")))
                .when(emailService).sendOtpEmail(eq(EMAIL), anyString(), eq(10));

        service.resendOtp(new ResendOtpRequest(FLOW_ID));

        verify(redisTemplate).delete(OTP_KEY);
        verify(redisTemplate).delete(FLOW_USER_KEY);
        verify(redisTemplate).delete("password-reset:flow:user:id:" + FLOW_ID);
        // The cooldown is always released so the user can retry immediately.
        verify(redisTemplate).delete(RESEND_KEY);
    }

    /** Verifies that resend otp email failure preserves flow keys when otp superseded. */
    @Test
    void resendOtp_emailFailure_preservesFlowKeys_whenOtpSuperseded() throws Exception {
        String existingOtpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256("111111", "test-hmac-secret"), 0));
        String supersedingOtpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256("222222", "test-hmac-secret"), 0));
        AtomicReference<String> storedOtp = new AtomicReference<>(existingOtpJson);
        when(valueOperations.get(OTP_KEY)).thenAnswer(inv -> storedOtp.get());
        org.mockito.stubbing.Answer<?> storeAnswer = inv -> {
            storedOtp.set(inv.getArgument(1));
            return null;
        };
        org.mockito.Mockito.doAnswer(storeAnswer).when(valueOperations)
                .set(eq(OTP_KEY), anyString(), eq(Duration.ofMinutes(10)));
        when(valueOperations.setIfAbsent(RESEND_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(valueOperations.get(FLOW_USER_KEY)).thenReturn(EMAIL);
        // Simulate a newer resend landing while this (older) delivery is in flight.
        org.mockito.Mockito.doAnswer(inv -> {
            storedOtp.set(supersedingOtpJson);
            throw new EmailSendingException("down", new RuntimeException("smtp"));
        }).when(emailService).sendOtpEmail(eq(EMAIL), anyString(), eq(10));

        service.resendOtp(new ResendOtpRequest(FLOW_ID));

        // A newer OTP superseded the failed one: the late failure must not delete it.
        verify(redisTemplate, never()).delete(OTP_KEY);
        verify(redisTemplate, never()).delete(FLOW_USER_KEY);
        verify(redisTemplate).delete(RESEND_KEY);
    }
}
