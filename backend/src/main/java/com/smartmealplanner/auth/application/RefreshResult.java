package com.smartmealplanner.auth.application;

import com.smartmealplanner.auth.persistence.ClientKind;

public record RefreshResult(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken,
        ClientKind clientKind) {

    public RefreshResult {

        if (accessToken == null) {
            throw new IllegalArgumentException(
                    "accessToken is required");
        }

        if (refreshToken == null) {
            throw new IllegalArgumentException(
                    "refreshToken is required");
        }

        if (clientKind == null
                || clientKind == ClientKind.UNKNOWN) {

            throw new IllegalArgumentException(
                    "clientKind is required");
        }
    }
}