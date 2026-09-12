package com.smartmealplanner.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.ClientKind;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAuthSession;
import com.smartmealplanner.auth.persistence.UserAuthSessionRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final UserAuthSessionRepository sessions;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(
            UserAuthSessionRepository sessions,

            @Value(
                    "${app.auth.refresh-token-ttl:PT720H}")
            String refreshTokenTtl) {

        Duration parsedTtl =
                Duration.parse(
                        refreshTokenTtl);

        if (parsedTtl.isZero()
                || parsedTtl.isNegative()) {

            throw new IllegalArgumentException(
                    "Refresh token TTL must be positive");
        }

        this.sessions = sessions;
        this.secureRandom = new SecureRandom();
        this.clock = Clock.systemUTC();
        this.refreshTokenTtl = parsedTtl;
    }

    @Transactional
    public IssuedRefreshToken issue(
            UserAccount account,
            ClientKind clientKind,
            byte[] ipAddress,
            String userAgent) {

        return createSession(
                account,
                clientKind,
                ipAddress,
                userAgent)
                .refreshToken();
    }

    @Transactional
    public CreatedRefreshSession createSession(
            UserAccount account,
            ClientKind clientKind,
            byte[] ipAddress,
            String userAgent) {

        if (account == null) {
            throw new IllegalArgumentException(
                    "account is required");
        }

        if (account.accountStatus()
                != AccountStatus.ACTIVE) {

            throw new IllegalStateException(
                    "Refresh tokens can only be issued to active accounts");
        }

        if (clientKind == null
                || clientKind == ClientKind.UNKNOWN) {

            throw new IllegalArgumentException(
                    "Supported client kind is required");
        }

        validateIpAddress(
                ipAddress);

        byte[] randomBytes =
                new byte[TOKEN_BYTES];

        secureRandom.nextBytes(
                randomBytes);

        String plaintextToken =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                randomBytes);

        String tokenHash =
                hashToken(
                        plaintextToken);

        Instant now =
                clock.instant();

        Instant expiresAt =
                now.plus(
                        refreshTokenTtl);

        LocalDateTime databaseExpiresAt =
                LocalDateTime.ofInstant(
                        expiresAt,
                        ZoneOffset.UTC);

        UserAuthSession session =
                new UserAuthSession(
                        account,
                        tokenHash,
                        databaseExpiresAt,
                        clientKind,
                        ipAddress,
                        userAgent);

        session =
                sessions.saveAndFlush(
                        session);

        return new CreatedRefreshSession(
                session,
                new IssuedRefreshToken(
                        plaintextToken,
                        expiresAt));
    }

    public String hashToken(
            String plaintextToken) {

        if (plaintextToken == null
                || plaintextToken.isBlank()) {

            throw new IllegalArgumentException(
                    "refresh token is required");
        }

        return sha256Hex(
                plaintextToken);
    }

    public LocalDateTime nowUtc() {

        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
    }

    private static String sha256Hex(
            String value) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256");

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    value.getBytes(
                                            StandardCharsets.UTF_8)));

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception);
        }
    }

    private static void validateIpAddress(
            byte[] ipAddress) {

        if (ipAddress == null) {
            return;
        }

        if (ipAddress.length != 4
                && ipAddress.length != 16) {

            throw new IllegalArgumentException(
                    "IP address must contain 4 or 16 bytes");
        }
    }
}