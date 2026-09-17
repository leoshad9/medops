package com.medops.assistant.domain;

/**
 * Thrown when a user exhausts their assistant chat rate limit. Mapped to 429 by
 * {@code GlobalExceptionHandler}.
 */
public class AssistantRateLimitException extends RuntimeException {

    public AssistantRateLimitException() {
        super("Too many assistant requests. Please try again shortly.");
    }
}