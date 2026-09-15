package com.medops.auth.security;

/**
 * Names of the HttpOnly cookies that carry the access and refresh tokens.
 * Shared between the controller that writes them and the filter that reads them.
 */
public final class CookieConstants {

    public static final String ACCESS = "MEDOPS_ACCESS";
    public static final String REFRESH = "MEDOPS_REFRESH";

    /**
     * Carries the short-lived password-reset token between OTP verification and the
     * reset call, so the token travels in an HttpOnly cookie rather than the URL
     * (where it leaks via browser history, server logs, and Referer headers).
     */
    public static final String PASSWORD_RESET = "MEDOPS_PASSWORD_RESET";

    private CookieConstants() {
    }
}

