package com.medops.auth.infrastructure.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.medops.shared.util.LogMasking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fire-and-forget delivery of the post-reset confirmation email via Spring's
 * {@code @Async} proxy, running on the bounded mail pool (see
 * {@link MailExecutorConfiguration}).
 *
 * <p>This is the one mail path whose failure handling is purely log-and-move-on
 * (the password reset itself has already committed when this runs), so the async
 * proxy can own the thread boundary. The OTP paths stay on {@link MailDeliveryExecutor}
 * because their failure handling needs the caller's reset-flow context.
 *
 * <p>Resilience (retry + circuit breaker) remains on {@link SmtpEmailService}, so it
 * executes on the worker thread and no {@code @Async}/resilience advisors are ever
 * stacked on the same proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetConfirmationSender {

    private final EmailService emailService;

    /**
     * Sends the confirmation email off the request thread.
     *
     * @param email the recipient address (never logged in the clear)
     */
    @Async
    public void send(String email) {
        try {
            emailService.sendPasswordResetConfirmationEmail(email);
        } catch (EmailSendingException e) {
            // Retries/circuit breaker on SmtpEmailService already ran; the reset itself
            // succeeded, so delivery failure is log-only and must not surface anywhere.
            log.error("Password reset succeeded for {} but the confirmation email could not be sent",
                    LogMasking.maskEmail(email), e);
        }
    }
}
