package com.medops.auth.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.shared.response.ErrorDetail;
import com.medops.shared.response.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Guards the login and registration endpoints against brute-force/credential-stuffing and
 * signup-abuse traffic with a simple fixed-window limit per client IP.
 * <p>
 * This is intentionally an in-memory, single-instance limiter - the smallest change that
 * satisfies the requirement today. It does not share state across instances, so a
 * horizontally-scaled deployment needs a shared store (e.g. Redis) instead before that becomes
 * the deployment shape.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public final class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login", "/api/v1/patients", "/api/v1/doctors");
    private static final int MAX_ATTEMPTS_PER_WINDOW = 10;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    private final ObjectMapper objectMapper;
    private final Map<String, Window> windowsByClient = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = request.getRequestURI() + '|' + request.getRemoteAddr();
        if (!tryAcquire(clientKey)) {
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(String clientKey) {
        long now = System.currentTimeMillis();
        Window window = windowsByClient.computeIfAbsent(clientKey, key -> new Window(now));

        synchronized (window) {
            if (now - window.windowStartMillis >= WINDOW_MILLIS) {
                window.windowStartMillis = now;
                window.count = 0;
            }
            if (window.count >= MAX_ATTEMPTS_PER_WINDOW) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(ErrorDetail.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RESOURCE_EXHAUSTED",
                "Too many attempts. Please try again later.",
                null));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static final class Window {
        private long windowStartMillis;
        private int count;

        private Window(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}
