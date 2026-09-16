package com.smartmealplanner.auth.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpAuthEmailDeliveryTest {

    private static final String TOKEN =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    void verificationEmailUsesFlutterHashRoute() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpAuthEmailDelivery delivery =
                new SmtpAuthEmailDelivery(
                        sender,
                        "no-reply@example.test",
                        "http://localhost:3000/");

        delivery.sendEmailVerification(
                "person@example.test",
                TOKEN);

        assertThat(capturedBody(sender))
                .contains(
                        "http://localhost:3000/#/auth/verify-email?token="
                                + TOKEN);
    }

    @Test
    void passwordResetEmailUsesFlutterHashRoute() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpAuthEmailDelivery delivery =
                new SmtpAuthEmailDelivery(
                        sender,
                        "no-reply@example.test",
                        "http://localhost:3000");

        delivery.sendPasswordReset(
                "person@example.test",
                TOKEN);

        assertThat(capturedBody(sender))
                .contains(
                        "http://localhost:3000/#/auth/reset-password?token="
                                + TOKEN);
    }

    private static String capturedBody(
            JavaMailSender sender) {

        ArgumentCaptor<SimpleMailMessage> messages =
                ArgumentCaptor.forClass(
                        SimpleMailMessage.class);

        verify(sender).send(
                messages.capture());

        return messages.getValue().getText();
    }
}
