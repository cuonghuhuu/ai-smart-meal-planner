package com.smartmealplanner.auth.application;

public interface AuthEmailDelivery {

    void sendEmailVerification(
            String recipientEmail,
            String plaintextToken);

    void sendPasswordReset(
            String recipientEmail,
            String plaintextToken);
}
