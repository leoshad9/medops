package com.medops.auth.dto;

/**
 * Issued access/refresh token pair.
 *
 * @param expiresIn access token lifetime in seconds
 */
public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public static AuthResponse of(String accessToken, String refreshToken, long accessTokenExpiryMs) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", accessTokenExpiryMs / 1000);
    }
}
