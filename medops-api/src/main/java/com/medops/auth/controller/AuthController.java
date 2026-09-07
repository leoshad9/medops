package com.medops.auth.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.auth.dto.LoginRequest;
import com.medops.auth.dto.UserInfo;
import com.medops.auth.exception.InvalidRefreshTokenException;
import com.medops.auth.security.CookieConstants;
import com.medops.auth.security.JwtService;
import com.medops.auth.service.AuthService;
import com.medops.auth.service.SessionResult;
import com.medops.shared.response.ApiResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public final class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserInfo>> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        SessionResult result = authService.login(request);
        setAuthCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(result.user(), "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UserInfo>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(request);
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException();
        }
        SessionResult result = authService.refresh(refreshToken);
        setAuthCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(result.user(), "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(request);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }

    private String resolveRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (CookieConstants.REFRESH.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(CookieConstants.ACCESS, accessToken,
                Duration.ofMillis(jwtService.getAccessTokenExpiryMs())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(CookieConstants.REFRESH, refreshToken,
                Duration.ofMillis(jwtService.getRefreshTokenExpiryMs())).toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(CookieConstants.ACCESS, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(CookieConstants.REFRESH, "", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
