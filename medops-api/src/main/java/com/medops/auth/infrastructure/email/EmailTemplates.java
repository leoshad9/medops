package com.medops.auth.infrastructure.email;

/**
 * Plain-text bodies for account emails, kept out of {@link SmtpEmailService}
 * so the service stays focused on delivery. If templates grow beyond simple
 * text blocks, graduate to a template engine (Thymeleaf/Freemarker) rather
 * than growing this class.
 */
final class EmailTemplates {

    static final String PASSWORD_RESET_CONFIRMATION_TEXT = """
            Hi,

            Your MedOps password has been successfully reset.

            If you didn't request this change, please contact support immediately.

            MedOps
            """;

    /** Prevents instantiation of this template utility. */
    private EmailTemplates() {
    }

    /** Builds the plain-text password-recovery OTP email. */
    static String otpEmail(String otp, int ttlMinutes) {
        return """
                Hi,

                Your MedOps password reset OTP is:

                %s

                This OTP expires in %d minutes.

                If you didn't request a password reset, you can ignore this email.

                MedOps
                """.formatted(otp, ttlMinutes);
    }
}

