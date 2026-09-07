package com.medops.auth.security;

/**
 * Names of the HttpOnly cookies that carry the access and refresh tokens.
 * Shared between the controller that writes them and the filter that reads them.
 */
public final class CookieConstants {

    public static final String ACCESS = "MEDOPS_ACCESS";
    public static final String REFRESH = "MEDOPS_REFRESH";

    private CookieConstants() {
    }
}