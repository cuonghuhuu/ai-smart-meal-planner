package com.smartmealplanner.auth.application;

import com.smartmealplanner.auth.persistence.ClientKind;

public record LoginResult(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken,
        ClientKind clientKind) {

    public LoginResult {

        if (accessToken == null) {
            throw new IllegalArgumentException(
                    "accessToken is required");
        }

        if (refreshToken == null) {
            throw new IllegalArgumentException(
                    "refreshToken is required");
        }

        if (clientKind == null) {
            throw new IllegalArgumentException(
                    "clientKind is required");
        }
    }
}