package com.medops.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Answers requests that reach a protected endpoint without usable credentials - no
 * {@code Authorization} header, or a malformed/expired access token - with 401 rather than
 * Spring Security's default empty 403. The message stays generic so it cannot be used to
 * distinguish "no token" from "expired token" or to probe which accounts exist.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
    }
}
