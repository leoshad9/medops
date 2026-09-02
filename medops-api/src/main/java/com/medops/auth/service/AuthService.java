package com.medops.auth.service;

import java.time.Duration;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.dto.AuthResponse;
import com.medops.auth.dto.LoginRequest;
import com.medops.auth.entity.RefreshToken;
import com.medops.auth.entity.User;
import com.medops.auth.exception.InvalidRefreshTokenException;
import com.medops.auth.repository.RefreshTokenRepository;
import com.medops.auth.repository.UserRepository;
import com.medops.auth.security.JwtService;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final TokenIssuanceService tokenIssuanceService;
    private final RateLimiterStore rateLimiterStore;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String lockKey = lockoutKey(request.email());
        if (!rateLimiterStore.tryAcquire(lockKey, MAX_FAILED_LOGINS, LOCKOUT_WINDOW)) {
            auditService.recordEvent(AuditEventType.AUTH_LOGIN_LOCKED, null, request.email());
            throw new LockedException("Too many failed sign-in attempts. Try again later.");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            auditService.recordEvent(AuditEventType.AUTH_LOGIN_FAILURE, null, request.email());
            throw e;
        }

        rateLimiterStore.reset(lockKey);
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + request.email()));

        auditService.recordEvent(AuditEventType.AUTH_LOGIN_SUCCESS, user.getId(), user.getEmail());
        return tokenIssuanceService.issue(user);
    }

    private static String lockoutKey(String email) {
        return "login-lock|" + email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);

        if (existingToken == null || !existingToken.isValid()) {
            auditService.recordEvent(AuditEventType.AUTH_TOKEN_REFRESH_FAILURE, null, null);
            throw new InvalidRefreshTokenException();
        }

        existingToken.revoke();
        refreshTokenRepository.save(existingToken);

        User user = existingToken.getUser();
        auditService.recordEvent(AuditEventType.AUTH_TOKEN_REFRESH_SUCCESS, user.getId(), user.getEmail());
        return tokenIssuanceService.issue(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
            User user = token.getUser();
            auditService.recordEvent(AuditEventType.AUTH_LOGOUT, user.getId(), user.getEmail());
        });
    }
}
