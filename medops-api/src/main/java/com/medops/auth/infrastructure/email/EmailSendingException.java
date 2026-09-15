package com.medops.auth.infrastructure.email;

/**
 * Thrown when an email cannot be delivered (SMTP unreachable, auth failure,
 * rejected recipient, ...). Callers decide whether the surrounding operation
 * should fail or degrade; see {@link GlobalExceptionHandler} for the HTTP
 * mapping.
 */
public class EmailSendingException extends RuntimeException {

    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}

