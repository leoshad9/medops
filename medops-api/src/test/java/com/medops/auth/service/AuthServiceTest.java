package com.medops.auth.service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import com.medops.auth.dto.AuthResponse;
import com.medops.auth.dto.LoginRequest;
import com.medops.auth.entity.RefreshToken;
import com.medops.auth.entity.User;
import com.medops.auth.entity.UserStatus;
import com.medops.auth.exception.InvalidRefreshTokenException;
import com.medops.auth.repository.RefreshTokenRepository;
import com.medops.auth.repository.UserRepository;
import com.medops.auth.security.JwtService;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}. Pure Mockito - no Spring context - covering the
 * login/refresh/logout use cases and the audit events each one must record.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "patient@medops.dev";
    private static final String PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String REFRESH_TOKEN_HASH = "hashed-refresh-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuditService auditService;
    @Mock
    private TokenIssuanceService tokenIssuanceService;
    @Mock
    private RateLimiterStore rateLimiterStore;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_returnsTokensOnSuccessfulAuthentication() {
        User user = userWithId(EMAIL);
        when(rateLimiterStore.tryAcquire(any(), eq(5), any())).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        stubIssueTokens();

        AuthResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(rateLimiterStore).reset("login-lock|" + EMAIL);
        verify(auditService).recordEvent(AuditEventType.AUTH_LOGIN_SUCCESS, user.getId(), EMAIL);
    }

    @Test
    void login_recordsFailureAuditAndPropagatesException_onBadCredentials() {
        when(rateLimiterStore.tryAcquire(any(), eq(5), any())).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> authService.login(request));

        assertThat(exception.getMessage()).isEqualTo("bad credentials");
        verify(auditService).recordEvent(AuditEventType.AUTH_LOGIN_FAILURE, null, EMAIL);
        verify(rateLimiterStore, never()).reset(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_locksAfterFailedAttemptBudget() {
        when(rateLimiterStore.tryAcquire(any(), eq(5), any())).thenReturn(false);
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        LockedException exception = assertThrows(LockedException.class, () -> authService.login(request));

        assertThat(exception.getMessage()).contains("Too many failed sign-in attempts");
        verify(auditService).recordEvent(AuditEventType.AUTH_LOGIN_LOCKED, null, EMAIL);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void refresh_rotatesValidTokenAndReturnsNewPair() {
        User user = userWithId(EMAIL);
        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(REFRESH_TOKEN_HASH)
                .expiresAt(ZonedDateTime.now(ZoneOffset.UTC).plusDays(1))
                .build();
        when(jwtService.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(existingToken));
        stubIssueTokens();

        AuthResponse response = authService.refresh(RAW_REFRESH_TOKEN);

        assertNotNull(existingToken.getRevokedAt(), "presented refresh token should be revoked on rotation");
        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(auditService).recordEvent(AuditEventType.AUTH_TOKEN_REFRESH_SUCCESS, user.getId(), EMAIL);
    }

    @Test
    void refresh_throwsAndRecordsFailure_whenTokenNotFound() {
        when(jwtService.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.empty());

        InvalidRefreshTokenException exception =
                assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(RAW_REFRESH_TOKEN));

        assertThat(exception.getMessage()).isEqualTo("Refresh token is invalid, expired, or revoked");
        verify(auditService).recordEvent(eq(AuditEventType.AUTH_TOKEN_REFRESH_FAILURE), isNull(), isNull());
    }

    @Test
    void refresh_throwsAndRecordsFailure_whenTokenExpired() {
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(userWithId(EMAIL))
                .tokenHash(REFRESH_TOKEN_HASH)
                .expiresAt(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();
        when(jwtService.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(expiredToken));

        InvalidRefreshTokenException exception =
                assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(RAW_REFRESH_TOKEN));

        assertThat(exception.getMessage()).isEqualTo("Refresh token is invalid, expired, or revoked");
        verify(refreshTokenRepository, never()).save(anyRefreshToken());
    }

    @Test
    void logout_revokesTokenAndRecordsAudit_whenTokenFound() {
        User user = userWithId(EMAIL);
        RefreshToken token = activeRefreshToken(user);
        when(jwtService.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(token));

        authService.logout(RAW_REFRESH_TOKEN);

        assertNotNull(token.getRevokedAt());
        verify(refreshTokenRepository).save(token);
        verify(auditService).recordEvent(AuditEventType.AUTH_LOGOUT, user.getId(), EMAIL);
    }

    @Test
    void logout_isNoOp_whenTokenNotFound() {
        when(jwtService.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.empty());

        authService.logout(RAW_REFRESH_TOKEN);

        verify(refreshTokenRepository, never()).save(anyRefreshToken());
        verify(auditService, never()).recordEvent(any(), any(), any());
    }

    private void stubIssueTokens() {
        when(tokenIssuanceService.issue(any(User.class)))
                .thenReturn(AuthResponse.of(ACCESS_TOKEN, RAW_REFRESH_TOKEN, 900_000L));
    }

    @SuppressWarnings("null") // Mockito's any() returns a placeholder null while recording the matcher stack
    private static @NonNull RefreshToken anyRefreshToken() {
        return any(RefreshToken.class);
    }

    private static @NonNull RefreshToken activeRefreshToken(User user) {
        return Objects.requireNonNull(RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(REFRESH_TOKEN_HASH)
                .expiresAt(ZonedDateTime.now(ZoneOffset.UTC).plusDays(1))
                .build());
    }

    private static User userWithId(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(ENCODED_PASSWORD)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
