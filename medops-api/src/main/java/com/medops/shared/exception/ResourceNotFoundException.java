package com.medops.shared.exception;

/**
 * Thrown when a requested resource does not exist, or must be hidden as absent
 * so callers cannot probe identifiers they do not own.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
