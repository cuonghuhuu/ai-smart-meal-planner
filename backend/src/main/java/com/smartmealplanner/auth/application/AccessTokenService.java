package com.smartmealplanner.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public AccessTokenService(
            JwtEncoder jwtEncoder,

            @Value(
                    "${app.auth.jwt.issuer:smart-meal-planner}")
            String issuer,

            @Value(
                    "${app.auth.jwt.access-token-ttl:PT15M}")
            String accessTokenTtl) {

        if (issuer == null
                || issuer.isBlank()) {

            throw new IllegalArgumentException(
                    "JWT issuer is required");
        }

        Duration parsedTtl =
                Duration.parse(
                        accessTokenTtl);

        if (parsedTtl.isZero()
                || parsedTtl.isNegative()) {

            throw new IllegalArgumentException(
                    "Access token TTL must be positive");
        }

        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = parsedTtl;
        this.clock = Clock.systemUTC();
    }

    public IssuedAccessToken issue(
            AuthenticatedUser user) {

        if (user == null) {
            throw new IllegalArgumentException(
                    "user is required");
        }

        Instant issuedAt =
                clock.instant();

        Instant expiresAt =
                issuedAt.plus(
                        accessTokenTtl);

        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .subject(
                                user.publicId()
                                        .toString())
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .id(
                                UUID.randomUUID()
                                        .toString())
                        .claim(
                                "token_type",
                                "access")
                        .claim(
                                "roles",
                                user.roles())
                        .build();

        JwsHeader header =
                JwsHeader
                        .with(
                                SignatureAlgorithm.RS256)
                        .build();

        String token =
                jwtEncoder
                        .encode(
                                JwtEncoderParameters.from(
                                        header,
                                        claims))
                        .getTokenValue();

        return new IssuedAccessToken(
                token,
                expiresAt);
    }
}