package com.smartmealplanner.auth.web;

import java.time.Instant;

public record AndroidRefreshResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}