package com.medops.auth.service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.dto.AuthResponse;
import com.medops.auth.entity.RefreshToken;
import com.medops.auth.entity.User;
import com.medops.auth.repository.RefreshTokenRepository;
import com.medops.auth.security.JwtService;
import com.medops.auth.security.MedOpsUserDetailsService;

import lombok.RequiredArgsConstructor;

/**
 * Issues an access/refresh token pair for an already-authenticated {@link User}. Shared by
 * login, token refresh, and registration (which auto-signs the new account in) so the
 * access-token claims, refresh-token persistence, and expiry handling live in exactly one place.
 */
@Service
@RequiredArgsConstructor
public class TokenIssuanceService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MedOpsUserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse issue(User user) {
        UserDetails userDetails = userDetailsService.buildUserDetails(user);

        String accessToken = jwtService.generateAccessToken(userDetails);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken = Objects.requireNonNull(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(ZonedDateTime.now(ZoneOffset.UTC)
                        .plus(Duration.ofMillis(jwtService.getRefreshTokenExpiryMs())))
                .build());
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessTokenExpiryMs());
    }
}
