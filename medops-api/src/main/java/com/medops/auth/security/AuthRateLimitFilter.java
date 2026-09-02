package com.medops.auth.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.exception.ServiceUnavailableException;
import com.medops.shared.response.ErrorDetail;
import com.medops.shared.response.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Guards login, token refresh, registration, booking, and clinical upload POSTs with a shared
 * fixed-window rate limit (Redis in multi-instance deployments). Fail closed when the store is
 * unavailable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public final class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/v1/patients",
            "/api/v1/doctors",
            "/api/v1/appointments");
    private static final int MAX_ATTEMPTS_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper;
    private final RateLimiterStore rateLimiterStore;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !isLimitedPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = request.getRequestURI() + '|' + request.getRemoteAddr();
        try {
            if (!rateLimiterStore.tryAcquire(clientKey, MAX_ATTEMPTS_PER_WINDOW, WINDOW)) {
                writeTooManyRequests(response);
                return;
            }
        } catch (ServiceUnavailableException ex) {
            writeUnavailable(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isLimitedPath(String uri) {
        if (LIMITED_PATHS.contains(uri)) {
            return true;
        }
        return uri.startsWith("/api/v1/patients/")
                && (uri.endsWith("/reports") || uri.endsWith("/prescriptions"));
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

    private void writeUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(ErrorDetail.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "RESOURCE_EXHAUSTED",
                "Service temporarily unavailable. Please try again later.",
                null));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
