package com.smartmealplanner.auth.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.SecurityTokenKind;
import com.smartmealplanner.auth.persistence.SessionRevocationReason;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserAuthSession;
import com.smartmealplanner.auth.persistence.UserAuthSessionRepository;
import com.smartmealplanner.auth.persistence.UserSecurityToken;
import com.smartmealplanner.auth.persistence.UserSecurityTokenRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserAccountRepository accounts;
    private final UserSecurityTokenRepository securityTokenRepository;
    private final UserAuthSessionRepository sessions;
    private final SecurityTokenService securityTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthEmailDelivery emailDelivery;

    public PasswordResetService(
            UserAccountRepository accounts,
            UserSecurityTokenRepository securityTokenRepository,
            UserAuthSessionRepository sessions,
            SecurityTokenService securityTokens,
            PasswordEncoder passwordEncoder,
            AuthEmailDelivery emailDelivery) {

        this.accounts = accounts;
        this.securityTokenRepository =
                securityTokenRepository;
        this.sessions = sessions;
        this.securityTokens = securityTokens;
        this.passwordEncoder = passwordEncoder;
        this.emailDelivery = emailDelivery;
    }

    @Transactional
    public Optional<String> requestReset(
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
                != AccountStatus.ACTIVE) {

            return Optional.empty();
        }

        String plaintextToken =
                securityTokens.createPasswordResetToken(
                        account);

        try {
            emailDelivery.sendPasswordReset(
                    account.email(),
                    plaintextToken);

        } catch (AuthEmailDeliveryException exception) {
            /*
             * Forgot-password must not reveal whether an account exists.
             * A syntactically valid request therefore keeps the same HTTP
             * result even if SMTP delivery fails for a real account.
             */
        }

        return Optional.of(
                plaintextToken);
    }

    @Transactional
    public void resetPassword(
            String plaintextToken,
            String rawPassword) {

        PasswordPolicy.validate(
                rawPassword);

        String tokenHash;

        try {
            tokenHash =
                    securityTokens.hashToken(
                            plaintextToken);

        } catch (IllegalArgumentException exception) {
            throw new InvalidPasswordResetTokenException();
        }

        UserSecurityToken token =
                securityTokenRepository.findUsableForUpdate(
                                tokenHash,
                                SecurityTokenKind.PASSWORD_RESET)
                        .orElseThrow(
                                InvalidPasswordResetTokenException::new);

        UserAccount account =
                token.user();

        if (account.accountStatus()
                != AccountStatus.ACTIVE) {

            throw new InvalidPasswordResetTokenException();
        }

        LocalDateTime now =
                securityTokens.nowUtc();

        account.changePassword(
                passwordEncoder.encode(
                        rawPassword),
                now);

        token.consume(
                now);

        revokeActiveSessions(
                account,
                now);

        accounts.flush();
        securityTokenRepository.flush();
        sessions.flush();
    }

    private void revokeActiveSessions(
            UserAccount account,
            LocalDateTime now) {

        List<UserAuthSession> activeSessions =
                sessions.findActiveByUserIdForUpdate(
                        account.internalId());

        for (UserAuthSession session :
                activeSessions) {

            session.revoke(
                    now,
                    SessionRevocationReason.PASSWORD_CHANGE,
                    null);
        }
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
