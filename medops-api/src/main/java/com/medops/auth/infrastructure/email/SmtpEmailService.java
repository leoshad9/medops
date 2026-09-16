package com.medops.auth.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.medops.shared.util.LogMasking;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    /**
     * Creates the SMTP email service.
     *
     * @param mailSender the Spring mail sender
     * @param from the configured sender address
     */
    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${spring.mail.from:MedOps <noreply@medops.dev>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * Sends a one-time password email with resilience4j retry + circuit breaker.
     *
     * @param to the recipient address (masked in logs)
     * @param otp the one-time password (never logged)
     * @param ttlMinutes the OTP validity window in minutes
     */
    @CircuitBreaker(name = "smtpMail", fallbackMethod = "sendOtpEmailFallback")
    @Retry(name = "smtpMail")
    @Override
    public void sendOtpEmail(String to, String otp, int ttlMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("MedOps Password Reset OTP");
            helper.setText(EmailTemplates.otpEmail(otp, ttlMinutes), false);
            mailSender.send(message);
            log.info("OTP email sent");
        } catch (MessagingException | MailException e) {
            log.error("Failed to send OTP email to {}", LogMasking.maskEmail(to), e);
            throw new EmailSendingException("Failed to send OTP email", e);
        }
    }

    /** Reports an OTP delivery failure after retries are exhausted. */
    @SuppressWarnings("unused")
    private void sendOtpEmailFallback(String to, String otp, int ttlMinutes, Exception e) {
        log.error("Circuit open - OTP email not delivered to {}: {}",
                LogMasking.maskEmail(to), e.getMessage());
        throw new EmailSendingException("Email service temporarily unavailable", e);
    }

    /**
     * Sends the post-reset confirmation email with resilience4j retry + circuit breaker.
     *
     * @param to the recipient address (masked in logs)
     */
    @CircuitBreaker(name = "smtpMail", fallbackMethod = "sendPasswordResetConfirmationEmailFallback")
    @Retry(name = "smtpMail")
    @Override
    public void sendPasswordResetConfirmationEmail(String to) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("MedOps Password Reset Successful");
            helper.setText(EmailTemplates.PASSWORD_RESET_CONFIRMATION_TEXT, false);
            mailSender.send(message);
            log.info("Password reset confirmation email sent");
        } catch (MessagingException | MailException e) {
            log.error("Failed to send password reset confirmation email to {}",
                    LogMasking.maskEmail(to), e);
            throw new EmailSendingException("Failed to send confirmation email", e);
        }
    }

    /** Reports a confirmation-email failure after retries are exhausted. */
    @SuppressWarnings("unused")
    private void sendPasswordResetConfirmationEmailFallback(String to, Exception e) {
        log.error("Circuit open - confirmation email not delivered to {}: {}",
                LogMasking.maskEmail(to), e.getMessage());
        throw new EmailSendingException("Email service temporarily unavailable", e);
    }
}
