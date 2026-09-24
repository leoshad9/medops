package com.medops.auth.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.medops.shared.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public final class CsrfController {

    private final CookieCsrfTokenRepository csrfTokenRepository;

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<Void>> csrf(HttpServletRequest request, HttpServletResponse response) {
        // Reuse the existing cookie token when present: rotation on every GET forces
        // a SecureRandom + Set-Cookie round trip (and invalidates tokens other tabs
        // already hold). CookieCsrfTokenRepository is stateless — no DB involved —
        // so the win is avoiding redundant crypto + cookie churn on prefetch calls.
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "CSRF token refreshed"));
    }
}
