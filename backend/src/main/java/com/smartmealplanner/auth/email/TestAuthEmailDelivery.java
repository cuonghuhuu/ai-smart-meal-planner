package com.smartmealplanner.auth.email;

import com.smartmealplanner.auth.application.AuthEmailDelivery;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class TestAuthEmailDelivery
        implements AuthEmailDelivery {

    @Override
    public void sendEmailVerification(
            String recipientEmail,
            String plaintextToken) {

        /*
         * Intentionally no-op.
         *
         * Tests exercise token persistence and consumption directly.
         * Plaintext security tokens must never be logged.
         */
    }

    @Override
    public void sendPasswordReset(
            String recipientEmail,
            String plaintextToken) {

        /*
         * Intentionally no-op.
         *
         * Tests exercise token persistence and consumption directly.
         * Plaintext security tokens must never be logged.
         */
    }
}
