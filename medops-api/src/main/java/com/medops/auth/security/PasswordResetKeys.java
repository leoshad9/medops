package com.medops.auth.security;

/**
 * Central Redis key constants for the password-reset flow.
 * <p>
 * Keeping all key strings in one place prevents silent drift between services
 * (a typo in one service would make it miss data written by another â€” a bug
 * that only surfaces at runtime).
 */
public final class PasswordResetKeys {

    /** JSON-serialised {@code PasswordResetOtp} record. */
    public static final String OTP_PREFIX      = "password-reset:otp:";

    /** User e-mail address associated with the flow. */
    public static final String USER_PREFIX     = "password-reset:flow:user:";

    /** User UUID associated with the flow. */
    public static final String USER_ID_PREFIX  = "password-reset:flow:user:id:";

    /** Cooldown sentinel (SET NX EX) for resend throttling. */
    public static final String RESEND_PREFIX   = "password-reset:resend:";

    /** JSON-serialised {@code PasswordResetToken} record (keyed by SHA-256 hash). */
    public static final String TOKEN_PREFIX    = "password-reset:token:";

    private PasswordResetKeys() {
    }
}
