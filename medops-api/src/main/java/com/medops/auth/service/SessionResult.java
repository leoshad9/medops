package com.medops.auth.service;

import com.medops.auth.dto.UserInfo;

/**
 * Internal carrier for the tokens that must reach the controller (so it can set
 * them as HttpOnly cookies) together with the public user identity that goes in
 * the response body. Never returned directly to the client.
 */
public record SessionResult(String accessToken, String refreshToken, UserInfo user) {
}
