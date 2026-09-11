package com.smartmealplanner.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialAuthenticationService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserAccountRepository accounts;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;

    /*
     * Used when an email does not exist so authentication still performs
     * a bcrypt verification step instead of returning immediately.
     *
     * The value is generated once at application startup.
     */
    private final String dummyPasswordHash;

    public CredentialAuthenticationService(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            PasswordEncoder passwordEncoder) {

        this.accounts = accounts;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;

        this.dummyPasswordHash =
                passwordEncoder.encode(
                        "dummy-authentication-password");
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(
            String email,
            String rawPassword) {

        String normalizedEmail =
                normalizeEmail(email);

        validatePassword(rawPassword);

        Optional<UserAccount> accountCandidate =
                accounts.findByEmailNormalized(
                        normalizedEmail);

        if (accountCandidate.isEmpty()) {

            /*
             * Deliberately perform bcrypt work for an unknown email
             * to reduce the timing difference between unknown-account
             * and wrong-password failures.
             */
            passwordEncoder.matches(
                    rawPassword,
                    dummyPasswordHash);

            throw new AuthenticationFailedException();
        }

        UserAccount account =
                accountCandidate.get();

        if (!passwordEncoder.matches(
                rawPassword,
                account.passwordHash())) {

            throw new AuthenticationFailedException();
        }

        /*
         * Only verified/active accounts may authenticate.
         *
         * Use the same public failure as an incorrect credential so
         * the endpoint does not expose account state.
         */
        if (account.accountStatus()
                != AccountStatus.ACTIVE) {

            throw new AuthenticationFailedException();
        }

        List<String> roles =
                userRoles.findRoleCodesByUserId(
                        account.internalId());

        if (roles.isEmpty()) {
            throw new IllegalStateException(
                    "Authenticated user has no roles");
        }

        return new AuthenticatedUser(
                account,
                account.publicId(),
                roles);
    }

    private static String normalizeEmail(
            String email) {

        if (email == null) {
            throw new AuthenticationFailedException();
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(
            String password) {

        if (password == null
                || password.isBlank()) {

            throw new AuthenticationFailedException();
        }

        if (password
                .getBytes(StandardCharsets.UTF_8)
                .length
                > BCRYPT_MAX_BYTES) {

            throw new AuthenticationFailedException();
        }
    }
}