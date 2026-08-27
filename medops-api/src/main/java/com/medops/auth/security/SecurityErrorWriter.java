package com.medops.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.shared.response.ErrorDetail;
import com.medops.shared.response.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Serialises security failures that occur inside the filter chain into the same
 * {@link ErrorResponse} envelope that {@code GlobalExceptionHandler} produces for
 * exceptions raised during request handling. Without this, Spring Security answers with
 * an empty body, which would make the two error surfaces inconsistent for clients.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, String semanticStatus, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(ErrorDetail.of(status.value(), semanticStatus, message));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
