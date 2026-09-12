package com.smartmealplanner.auth.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.SecurityTokenKind;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserSecurityToken;
import com.smartmealplanner.auth.persistence.UserSecurityTokenRepository;

@Service
public class EmailVerificationService {

    private final UserSecurityTokenRepository tokens;
    private final SecurityTokenService securityTokens;

    public EmailVerificationService(
            UserSecurityTokenRepository tokens,
            SecurityTokenService securityTokens) {

        this.tokens = tokens;
        this.securityTokens = securityTokens;
    }

    @Transactional
    public void verify(
            String plaintextToken) {

        String tokenHash;

        try {
            tokenHash =
                    securityTokens.hashToken(
                            plaintextToken);

        } catch (IllegalArgumentException exception) {
            throw new InvalidEmailVerificationTokenException();
        }

        /*
         * Expiry and consumed-state validation are performed by the
         * database query itself.
         *
         * MySQL is therefore the authoritative clock for token validity,
         * avoiding timezone-sensitive DATETIME/LocalDateTime comparisons
         * between the database and JVM.
         */
        UserSecurityToken token =
                tokens.findUsableForUpdate(
                                tokenHash,
                                SecurityTokenKind.EMAIL_VERIFICATION)
                        .orElseThrow(
                                InvalidEmailVerificationTokenException::new);

        UserAccount account =
                token.user();

        if (account.accountStatus()
                != AccountStatus.PENDING_VERIFICATION) {

            throw new InvalidEmailVerificationTokenException();
        }

        LocalDateTime now =
                securityTokens.nowUtc();

        account.verifyEmail(now);
        token.consume(now);
    }
}