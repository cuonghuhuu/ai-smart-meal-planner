package com.smartmealplanner.auth.web;

import java.time.Instant;

public record WebLoginResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt) {
}