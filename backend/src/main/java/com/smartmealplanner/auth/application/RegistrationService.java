package com.smartmealplanner.auth.application;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmealplanner.auth.persistence.Role;
import com.smartmealplanner.auth.persistence.RoleRepository;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRole;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

@Service
public class RegistrationService {

    private static final String DEFAULT_ROLE =
            "ROLE_USER";

    private final UserAccountRepository accounts;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    private final SecurityTokenService securityTokens;
    private final AuthEmailDelivery emailDelivery;

    public RegistrationService(
            UserAccountRepository accounts,
            RoleRepository roles,
            UserRoleRepository userRoles,
            PasswordEncoder passwordEncoder,
            SecurityTokenService securityTokens,
            AuthEmailDelivery emailDelivery) {

        this.accounts = accounts;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
        this.securityTokens = securityTokens;
        this.emailDelivery = emailDelivery;
    }

    @Transactional
    public RegistrationResult register(
            String email,
            String rawPassword,
            String displayName) {

        String normalizedEmail =
                normalizeEmail(
                        email);

        if (accounts.existsByEmailNormalized(
                normalizedEmail)) {

            throw new EmailAlreadyRegisteredException();
        }

        PasswordPolicy.validate(
                rawPassword);

        String passwordHash =
                passwordEncoder.encode(
                        rawPassword);

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                normalizedEmail,
                                passwordHash,
                                displayName));

        Role defaultRole =
                roles.findByCode(
                                DEFAULT_ROLE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Required ROLE_USER reference data is missing"));

        userRoles.save(
                new UserRole(
                        account,
                        defaultRole,
                        null));

        String verificationToken =
                securityTokens.createEmailVerificationToken(
                        account);

        emailDelivery.sendEmailVerification(
                account.email(),
                verificationToken);

        return new RegistrationResult(
                account.publicId(),
                account.accountStatus());
    }

    private static String normalizeEmail(
            String email) {

        if (email == null) {
            throw new IllegalArgumentException(
                    "email is required");
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
