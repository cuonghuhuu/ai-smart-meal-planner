package com.smartmealplanner.auth.application;

import java.util.Locale;
import java.util.Optional;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationResendService {

    private final UserAccountRepository accounts;
    private final SecurityTokenService securityTokens;
    private final AuthEmailDelivery emailDelivery;

    public VerificationResendService(
            UserAccountRepository accounts,
            SecurityTokenService securityTokens,
            AuthEmailDelivery emailDelivery) {

        this.accounts = accounts;
        this.securityTokens = securityTokens;
        this.emailDelivery = emailDelivery;
    }

    @Transactional
    public Optional<String> resend(
            String email) {

        String normalizedEmail =
                normalizeEmail(
                        email);

        Optional<UserAccount> candidate =
                accounts.findByEmailNormalizedForUpdate(
                        normalizedEmail);

        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        UserAccount account =
                candidate.get();

        if (account.accountStatus()
                != AccountStatus.PENDING_VERIFICATION) {

            return Optional.empty();
        }

        String plaintextToken =
                securityTokens.createEmailVerificationToken(
                        account);

        try {
            emailDelivery.sendEmailVerification(
                    account.email(),
                    plaintextToken);

        } catch (AuthEmailDeliveryException exception) {
            /*
             * Keep the HTTP behavior generic. Do not expose account
             * existence through different resend responses.
             */
        }

        return Optional.of(
                plaintextToken);
    }

    private static String normalizeEmail(
            String email) {

        if (email == null) {
            return "";
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
