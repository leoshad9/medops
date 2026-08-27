package com.medops.shared.response;

/**
 * Error response wrapper for public API endpoints.
 * Follows Google's error response pattern with consistent structure.
 */
public record ErrorResponse(boolean success, ErrorDetail error) {

    /**
     * Creates an error response with the provided error details.
     *
     * @param error the error information
     * @return wrapped error response
     */
    public static ErrorResponse of(ErrorDetail error) {
        return new ErrorResponse(false, error);
    }
}
