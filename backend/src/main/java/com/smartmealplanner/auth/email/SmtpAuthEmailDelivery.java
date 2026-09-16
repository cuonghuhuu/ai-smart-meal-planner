package com.smartmealplanner.auth.email;

import com.smartmealplanner.auth.application.AuthEmailDelivery;
import com.smartmealplanner.auth.application.AuthEmailDeliveryException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Profile("!test")
public class SmtpAuthEmailDelivery
        implements AuthEmailDelivery {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String webBaseUrl;

    public SmtpAuthEmailDelivery(
            JavaMailSender mailSender,
            @Value("${app.auth.email.from}")
            String fromAddress,
            @Value("${app.web.base-url}")
            String webBaseUrl) {

        this.mailSender = mailSender;
        this.fromAddress =
                requireText(
                        fromAddress,
                        "fromAddress");

        this.webBaseUrl =
                stripTrailingSlash(
                        requireText(
                                webBaseUrl,
                                "webBaseUrl"));
    }

    @Override
    public void sendEmailVerification(
            String recipientEmail,
            String plaintextToken) {

        String verificationUrl =
                authWebUrl(
                        "/auth/verify-email",
                        plaintextToken);

        send(
                recipientEmail,
                "Verify your Smart Meal Planner email",
                """
                Verify your email address to activate your Smart Meal Planner account.

                Open this link:
                %s

                If you did not create this account, you can ignore this email.
                """.formatted(
                        verificationUrl));
    }

    @Override
    public void sendPasswordReset(
            String recipientEmail,
            String plaintextToken) {

        String resetUrl =
                authWebUrl(
                        "/auth/reset-password",
                        plaintextToken);

        send(
                recipientEmail,
                "Reset your Smart Meal Planner password",
                """
                A password reset was requested for your Smart Meal Planner account.

                Open this link:
                %s

                If you did not request a password reset, you can ignore this email.
                """.formatted(
                        resetUrl));
    }

    private void send(
            String recipientEmail,
            String subject,
            String body) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(
                fromAddress);

        message.setTo(
                requireText(
                        recipientEmail,
                        "recipientEmail"));

        message.setSubject(
                subject);

        message.setText(
                body);

        try {
            mailSender.send(
                    message);

        } catch (MailException exception) {
            throw new AuthEmailDeliveryException(
                    exception);
        }
    }

    private static String requireText(
            String value,
            String field) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    field + " is required");
        }

        return value.trim();
    }

    private static String stripTrailingSlash(
            String value) {

        if (value.endsWith("/")) {
            return value.substring(
                    0,
                    value.length() - 1);
        }

        return value;
    }

    /**
     * Builds a URL for the Flutter web application's hash route. The token is
     * intentionally kept in the fragment query component so the browser does
     * not send it to the web server in a request path or query string.
     */
    private String authWebUrl(
            String route,
            String plaintextToken) {

        return UriComponentsBuilder.fromUriString(
                                webBaseUrl)
                .path(
                        "/")
                .fragment(
                        route + "?token=" + plaintextToken)
                .build()
                .encode()
                .toUriString();
    }
}
