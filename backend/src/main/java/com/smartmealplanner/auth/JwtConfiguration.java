package com.smartmealplanner.auth;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfiguration {

    @Bean
    KeyPair jwtSigningKeyPair(
            Environment environment,

            @Value(
                    "${app.auth.jwt.public-key-base64:}")
            String publicKeyBase64,

            @Value(
                    "${app.auth.jwt.private-key-base64:}")
            String privateKeyBase64) {

        boolean hasPublicKey =
                publicKeyBase64 != null
                        && !publicKeyBase64.isBlank();

        boolean hasPrivateKey =
                privateKeyBase64 != null
                        && !privateKeyBase64.isBlank();

        if (hasPublicKey != hasPrivateKey) {
            throw new IllegalStateException(
                    "Both JWT public and private keys must be configured");
        }

        if (hasPublicKey) {
            return loadConfiguredKeyPair(
                    publicKeyBase64,
                    privateKeyBase64);
        }

        if (environment.acceptsProfiles(
                Profiles.of("test"))) {

            return generateTestKeyPair();
        }

        throw new IllegalStateException(
                "JWT signing keys are not configured");
    }

    @Bean
    JwtEncoder jwtEncoder(
            KeyPair jwtSigningKeyPair) {

        RSAPublicKey publicKey =
                (RSAPublicKey)
                        jwtSigningKeyPair.getPublic();

        RSAPrivateKey privateKey =
                (RSAPrivateKey)
                        jwtSigningKeyPair.getPrivate();

        RSAKey rsaKey =
                new RSAKey.Builder(
                        publicKey)
                        .privateKey(
                                privateKey)
                        .keyID(
                                keyId(
                                        publicKey))
                        .build();

        ImmutableJWKSet<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(
                        new JWKSet(
                                rsaKey));

        return new NimbusJwtEncoder(
                jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(
            KeyPair jwtSigningKeyPair,

            @Value(
                    "${app.auth.jwt.issuer:smart-meal-planner}")
            String issuer) {

        RSAPublicKey publicKey =
                (RSAPublicKey)
                        jwtSigningKeyPair.getPublic();

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withPublicKey(
                                publicKey)
                        .signatureAlgorithm(
                                SignatureAlgorithm.RS256)
                        .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators
                        .createDefaultWithIssuer(
                                issuer);

        OAuth2TokenValidator<Jwt> tokenTypeValidator =
                new JwtClaimValidator<>(
                        "token_type",
                        "access"::equals);

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerValidator,
                        tokenTypeValidator));

        return decoder;
    }

    private static KeyPair loadConfiguredKeyPair(
            String publicKeyBase64,
            String privateKeyBase64) {

        try {
            KeyFactory keyFactory =
                    KeyFactory.getInstance(
                            "RSA");

            byte[] publicBytes =
                    Base64.getDecoder()
                            .decode(
                                    removeWhitespace(
                                            publicKeyBase64));

            byte[] privateBytes =
                    Base64.getDecoder()
                            .decode(
                                    removeWhitespace(
                                            privateKeyBase64));

            RSAPublicKey publicKey =
                    (RSAPublicKey)
                            keyFactory.generatePublic(
                                    new X509EncodedKeySpec(
                                            publicBytes));

            RSAPrivateKey privateKey =
                    (RSAPrivateKey)
                            keyFactory.generatePrivate(
                                    new PKCS8EncodedKeySpec(
                                            privateBytes));

            return new KeyPair(
                    publicKey,
                    privateKey);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "JWT signing keys are invalid",
                    exception);
        }
    }

    private static KeyPair generateTestKeyPair() {

        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance(
                            "RSA");

            generator.initialize(
                    2048);

            return generator.generateKeyPair();

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "RSA is not available",
                    exception);
        }
    }

    private static String keyId(
            RSAPublicKey publicKey) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256");

            byte[] fingerprint =
                    digest.digest(
                            publicKey.getEncoded());

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            fingerprint);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception);
        }
    }

    private static String removeWhitespace(
            String value) {

        return value.replaceAll(
                "\\s+",
                "");
    }
}