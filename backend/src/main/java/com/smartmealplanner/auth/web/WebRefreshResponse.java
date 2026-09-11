package com.smartmealplanner.auth.web;

import java.time.Instant;

public record WebRefreshResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt) {
}