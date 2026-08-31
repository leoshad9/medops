package com.medops.shared.exception;

/**
 * Raised when a required shared dependency (for example Redis for rate limiting) is unavailable.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
