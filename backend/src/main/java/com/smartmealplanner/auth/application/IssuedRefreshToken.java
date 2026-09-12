package com.smartmealplanner.auth.application;

import java.time.Instant;

public record IssuedRefreshToken(
        String token,
        Instant expiresAt) {

    public IssuedRefreshToken {

        if (token == null
                || token.isBlank()) {

            throw new IllegalArgumentException(
                    "token is required");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "expiresAt is required");
        }
    }
}