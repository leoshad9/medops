package com.medops.shared.response;

import java.util.List;

/**
 * Represents detailed error information following Google's API error format.
 * Contains HTTP status code, semantic status string, message, and validation details.
 *
 * @param code HTTP status code (e.g., 400, 404, 500)
 * @param status semantic error status (e.g., "INVALID_ARGUMENT", "NOT_FOUND")
 * @param message human-readable error description
 * @param details list of specific validation errors, null if not applicable
 */
public record ErrorDetail(int code, String status, String message, List<String> details) {

    /**
     * Creates an error detail without specific validation errors.
     *
     * @param code HTTP status code
     * @param status semantic error status
     * @param message error description
     * @return error detail instance
     */
    public static ErrorDetail of(int code, String status, String message) {
        return new ErrorDetail(code, status, message, null);
    }

    /**
     * Creates an error detail with validation errors.
     *
     * @param code HTTP status code
     * @param status semantic error status
     * @param message error description
     * @param details specific validation error messages
     * @return error detail instance
     */
    public static ErrorDetail of(int code, String status, String message, List<String> details) {
        return new ErrorDetail(code, status, message, details);
    }
}
