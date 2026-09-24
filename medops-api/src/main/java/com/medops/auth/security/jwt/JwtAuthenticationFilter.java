package com.medops.auth.security.jwt;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.medops.auth.security.CookieConstants;
import com.medops.auth.security.principal.MedOpsUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authenticates requests bearing a valid JWT access token, either in the
 * {@code Authorization} header (API clients) or the {@code MEDOPS_ACCESS} HttpOnly cookie
 * (browser SPA). Runs once per request, ahead of the standard username/password filter.
 */
@Component
@RequiredArgsConstructor
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final MedOpsUserDetailsService userDetailsService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Public endpoints never need an authenticated SecurityContext. Skipping JWT
        // parsing + the UserRepository lookup here keeps /api/auth/csrf (and other
        // auth endpoints) to pure token-cookie work instead of paying 2x JJWT
        // verifies + a users+roles DB round trip for a permitAll request.
        return uri.startsWith("/api/auth/")
                || uri.equals("/api/auth")
                || uri.startsWith("/actuator/health")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/swagger-ui/")
                || uri.startsWith("/api-docs/")
                || uri.startsWith("/webjars/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // validateAndExtractUsername swallows parse failures to Optional, so invalid
        // tokens just stay unauthenticated and the chain rejects them if the
        // endpoint requires authentication. No try/catch needed here.
        String username = jwtService.validateAndExtractUsername(token).orElse(null);
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (username.equals(userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (CookieConstants.ACCESS.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
