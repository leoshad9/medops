package com.medops.auth.infrastructure.email;

/**
 * Sends account/password-reset emails. Implementations must fail loudly
 * (throw {@link EmailSendingException}) rather than silently swallowing SMTP
 * errors, so callers know the OTP never reached the user.
 */
public interface EmailService {

    /**
     * Sends a one-time password email.
     *
     * @param to         the recipient address
     * @param otp        the verification code
     * @param ttlMinutes how long the OTP remains valid (for the email body)
     * @throws EmailSendingException if the email could not be delivered
     */
    void sendOtpEmail(String to, String otp, int ttlMinutes);

    /**
     * Sends a confirmation notice after a successful password reset.
     *
     * @param to the recipient address
     * @throws EmailSendingException if the email could not be delivered
     */
    void sendPasswordResetConfirmationEmail(String to);
}

