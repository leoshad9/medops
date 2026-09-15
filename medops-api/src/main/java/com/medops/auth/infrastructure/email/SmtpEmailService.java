package com.medops.auth.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${spring.mail.from:MedOps <noreply@medops.dev>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

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
            log.info("OTP email sent to {}", to);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send OTP email to {}", to, e);
            throw new EmailSendingException("Failed to send OTP email", e);
        }
    }

    @SuppressWarnings("unused")
    private void sendOtpEmailFallback(String to, String otp, int ttlMinutes, Exception e) {
        log.error("Circuit open - OTP email not delivered to {}: {}", to, e.getMessage());
        throw new EmailSendingException("Email service temporarily unavailable", e);
    }

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
            log.info("Password reset confirmation email sent to {}", to);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send password reset confirmation email to {}", to, e);
            throw new EmailSendingException("Failed to send confirmation email", e);
        }
    }

    @SuppressWarnings("unused")
    private void sendPasswordResetConfirmationEmailFallback(String to, Exception e) {
        log.error("Circuit open - confirmation email not delivered to {}: {}", to, e.getMessage());
        throw new EmailSendingException("Email service temporarily unavailable", e);
    }
}
