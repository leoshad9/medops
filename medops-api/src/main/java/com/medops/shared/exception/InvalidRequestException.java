package com.medops.shared.exception;

/**
 * Thrown when the request is syntactically valid but violates a domain rule
 * (wrong status, slot off the clinic grid, time in the past, ...).
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
