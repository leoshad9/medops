package com.medops.auth.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    private static final String FROM = "MedOps <noreply@medops.dev>";
    private static final String TO = "patient@medops.dev";

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailService emailService;

    /** Initializes the test fixtures. */
    @BeforeEach
    void setUp() {
        emailService = new SmtpEmailService(mailSender, FROM);
    }

    /** Verifies that send otp email builds and sends mime message. */
    @Test
    void sendOtpEmail_buildsAndSendsMimeMessage() throws Exception {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOtpEmail(TO, "123456", 10);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()[0]).hasToString(TO);
        assertThat(sent.getSubject()).isEqualTo("MedOps Password Reset OTP");
    }

    /** Verifies that send otp email throws email sending exception when mail sender fails. */
    @Test
    void sendOtpEmail_throwsEmailSendingException_whenMailSenderFails() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendOtpEmail(TO, "123456", 10))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Failed to send OTP email");
    }

    /** Verifies that send password reset confirmation email builds and sends mime message. */
    @Test
    void sendPasswordResetConfirmationEmail_buildsAndSendsMimeMessage() throws Exception {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordResetConfirmationEmail(TO);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()[0]).hasToString(TO);
        assertThat(sent.getSubject()).isEqualTo("MedOps Password Reset Successful");
    }

    /** Verifies that send password reset confirmation email throws email sending exception when mail sender fails. */
    @Test
    void sendPasswordResetConfirmationEmail_throwsEmailSendingException_whenMailSenderFails() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendPasswordResetConfirmationEmail(TO))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Failed to send confirmation email");
    }
}
