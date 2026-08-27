package com.medops.shared.response;

/**
 * Standard wrapper for successful API responses in the public API.
 * Provides a consistent contract for frontend clients.
 *
 * @param <T> the type of data being returned
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    /**
     * Creates a successful response with data and no message.
     *
     * @param <T> the type of data
     * @param data the response payload
     * @return wrapped success response
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * Creates a successful response with data and a message.
     *
     * @param <T> the type of data
     * @param data the response payload
     * @param message additional context for the client
     * @return wrapped success response with message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }
}