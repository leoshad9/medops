package com.medops.shared.exception;

/**
 * Thrown when a request would violate a uniqueness constraint the client controls
 * (e.g. registering with an email or license number that is already in use).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
