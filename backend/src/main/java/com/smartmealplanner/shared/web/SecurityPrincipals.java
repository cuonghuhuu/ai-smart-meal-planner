package com.smartmealplanner.shared.web;

import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityPrincipals {

    private SecurityPrincipals() {
    }

    public static UUID authenticatedPublicId(
            Jwt jwt) {

        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {

            throw new BadCredentialsException(
                    "Access token subject is invalid");
        }

        try {
            return UUID.fromString(
                    jwt.getSubject());

        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                    "Access token subject is invalid");
        }
    }
}
