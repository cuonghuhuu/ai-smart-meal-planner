package com.smartmealplanner.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.smartmealplanner.auth.persistence.SecurityTokenKind;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserSecurityToken;
import com.smartmealplanner.auth.persistence.UserSecurityTokenRepository;

@Service
public class SecurityTokenService {

    private static final int TOKEN_BYTES = 32;

    private final UserSecurityTokenRepository tokens;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public SecurityTokenService(UserSecurityTokenRepository tokens) {
        this.tokens = tokens;
        this.secureRandom = new SecureRandom();
        this.clock = Clock.systemUTC();
    }

    public String createEmailVerificationToken(UserAccount account) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        String plaintextToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = sha256Hex(plaintextToken);

        LocalDateTime now =
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        UserSecurityToken token =
                new UserSecurityToken(
                        account,
                        SecurityTokenKind.EMAIL_VERIFICATION,
                        tokenHash,
                        now.plusHours(24));

        tokens.save(token);

        return plaintextToken;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(
                            value.getBytes(StandardCharsets.UTF_8)));

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception);
        }
    }
}