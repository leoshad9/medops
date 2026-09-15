package com.medops.auth.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.domain.PasswordResetOtp;
import com.medops.auth.dto.passwordreset.VerifyOtpRequest;
import com.medops.auth.dto.passwordreset.VerifyOtpResponse;
import com.medops.auth.security.PasswordResetProperties;
import com.medops.auth.security.codec.PasswordResetCodec;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

/**
 * Unit tests for {@link VerifyOtpService}. Pure Mockito - no Spring context.
 *
 * The service claims the OTP record atomically via a Lua script (GET + TTL +
 * DEL in one round trip, so a concurrent resend can never interleave), so the
 * tests stub {@code redisTemplate.execute(script, keys)} rather than the old
 * {@code getAndDelete}/{@code getExpire} pair.
 */
@ExtendWith(MockitoExtension.class)
class VerifyOtpServiceTest {

    private static final String FLOW_ID = UUID.randomUUID().toString();
    private static final String OTP = "123456";
    private static final String EMAIL = "patient@medops.dev";
    private static final String OTP_KEY = "password-reset:otp:" + FLOW_ID;
    /** Remaining OTP lifetime returned by the atomic claim script. */
    private static final long OTP_TTL_SECONDS = 300L;

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
    private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordResetCodec codec = new PasswordResetCodec();

    private VerifyOtpService service;

    /**
     * Lifecycle: instantiated manually because {@code PROPERTIES} is a static
     * constant Mockito cannot supply via {@code @InjectMocks} constructor
     * injection. The IDE flags this as "never used" - it is invoked by JUnit's
     * {@code @BeforeEach}, which static analysis does not always trace.
     */
    @SuppressWarnings("unused")
    @BeforeEach
    void setUp() {
        service = new VerifyOtpService(redisTemplate, objectMapper, auditService,
                PROPERTIES, codec);
    }

    /** Stubs the atomic GET+TTL+DEL claim to return [otpJson, remainingTtlSeconds]. */
    @SuppressWarnings("unchecked")
    private void stubAtomicClaim(String otpJson) {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenAnswer(invocation -> {
            RedisScript<?> script = invocation.getArgument(0);
            String source = script.getScriptAsString();
            if (source != null && source.contains("ARGV[2]")) {
                // Compare-and-set restore script: report the write as applied.
                return 1L;
            }
            return List.of(otpJson, Long.toString(OTP_TTL_SECONDS));
        });
    }

    /** Verifies that verify otp issues reset token and cleans up flow on correct otp. */
    @Test
    void verifyOtp_issuesResetToken_andCleansUpFlow_onCorrectOtp() throws Exception {
        UUID userId = UUID.randomUUID();
        String otpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256(OTP, "test-hmac-secret"), 0));
        stubAtomicClaim(otpJson);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:flow:user:" + FLOW_ID)).thenReturn(EMAIL);
        when(valueOperations.get("password-reset:flow:user:id:" + FLOW_ID)).thenReturn(userId.toString());

        VerifyOtpResponse response = service.verifyOtp(new VerifyOtpRequest(FLOW_ID, OTP));

        assertThat(response.resetToken()).isNotNull();
        assertThat(response.resetToken()).doesNotContain(FLOW_ID); // never the raw flow id
        verify(valueOperations).set(startsWith("password-reset:token:"), anyString(), eq(Duration.ofMinutes(15)));
        verify(redisTemplate).delete(OTP_KEY);
        verify(redisTemplate).delete("password-reset:flow:user:" + FLOW_ID);
        verify(redisTemplate).delete("password-reset:flow:user:id:" + FLOW_ID);
        verify(redisTemplate).delete("password-reset:resend:" + FLOW_ID);
        verify(auditService).recordEventBestEffort(AuditEventType.PASSWORD_RESET_OTP_VERIFIED, null, EMAIL);
    }

    /** Verifies that verify otp increments attempts without extending ttl and audits on wrong otp. */
    @Test
    void verifyOtp_incrementsAttemptsWithoutExtendingTtl_andAudits_onWrongOtp() throws Exception {
        String otpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256(OTP, "test-hmac-secret"), 0));
        stubAtomicClaim(otpJson);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:flow:user:" + FLOW_ID)).thenReturn(EMAIL);

        VerifyOtpResponse response = service.verifyOtp(new VerifyOtpRequest(FLOW_ID, "000000"));

        assertThat(response.resetToken()).isNull();
        assertThat(response.message()).isEqualTo("Invalid OTP.");
        // The CAS restore script re-applies the remaining TTL so a failed attempt neither
        // drops the expiry nor extends it (plain SET would clear the TTL entirely).
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(OTP_KEY)),
                eq(otpJson), anyString(), eq(Long.toString(OTP_TTL_SECONDS)));
        verify(auditService).recordEventBestEffort(AuditEventType.PASSWORD_RESET_OTP_FAILED, null, EMAIL);
    }

    /** Verifies that verify otp returns generic failure when flow is gone or expired. */
    @Test
    @SuppressWarnings("unchecked")
    void verifyOtp_returnsGenericFailure_whenFlowIsGoneOrExpired() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        VerifyOtpResponse response = service.verifyOtp(new VerifyOtpRequest(FLOW_ID, OTP));

        assertThat(response.resetToken()).isNull();
        assertThat(response.message()).isEqualTo("Invalid or expired OTP.");
        verify(auditService, never()).recordEventBestEffort(any(AuditEventType.class), any(), any());
    }

    /** Verifies that verify otp returns recoverable error when redis claim fails. */
    @Test
    @SuppressWarnings("unchecked")
    void verifyOtp_returnsRecoverableError_whenRedisClaimFails() {
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenThrow(new RedisConnectionFailureException("down"));

        VerifyOtpResponse response = service.verifyOtp(new VerifyOtpRequest(FLOW_ID, OTP));

        assertThat(response.resetToken()).isNull();
        assertThat(response.message()).contains("temporarily unavailable");
        verify(auditService, never()).recordEventBestEffort(any(AuditEventType.class), any(), any());
    }

    /** Verifies that verify otp invalidates flow when failed attempt cannot be restored. */
    @Test
    @SuppressWarnings("unchecked")
    void verifyOtp_invalidatesFlow_whenFailedAttemptCannotBeRestored() throws Exception {
        String otpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256(OTP, "test-hmac-secret"), 0));
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(List.of(otpJson, Long.toString(OTP_TTL_SECONDS)));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:flow:user:" + FLOW_ID)).thenReturn(EMAIL);

        VerifyOtpResponse response = service.verifyOtp(new VerifyOtpRequest(FLOW_ID, "000000"));

        assertThat(response.message()).isEqualTo("Invalid OTP.");
        verify(redisTemplate).delete(OTP_KEY);
        verify(redisTemplate).delete("password-reset:flow:user:" + FLOW_ID);
        verify(redisTemplate).delete("password-reset:flow:user:id:" + FLOW_ID);
        verify(redisTemplate).delete("password-reset:resend:" + FLOW_ID);
    }

    /** Verifies that verify otp cleans up whole flow when max attempts reached. */
    @Test
    void verifyOtp_cleansUpWholeFlow_whenMaxAttemptsReached() throws Exception {
        String otpJson = objectMapper.writeValueAsString(
                new PasswordResetOtp(codec.hmacSha256(OTP, "test-hmac-secret"), 5));
        stubAtomicClaim(otpJson);

        VerifyOtpResponse response = service.verifyOtp(new VerifyOtpRequest(FLOW_ID, OTP));

        assertThat(response.resetToken()).isNull();
        assertThat(response.message()).contains("Too many failed attempts");
        verify(redisTemplate).delete(OTP_KEY);
        verify(redisTemplate).delete("password-reset:flow:user:" + FLOW_ID);
        verify(redisTemplate).delete("password-reset:flow:user:id:" + FLOW_ID);
        verify(redisTemplate).delete("password-reset:resend:" + FLOW_ID);
    }
}
