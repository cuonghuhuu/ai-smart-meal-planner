package com.smartmealplanner.auth.application;

import com.smartmealplanner.auth.persistence.UserAuthSession;

public record CreatedRefreshSession(
        UserAuthSession session,
        IssuedRefreshToken refreshToken) {

    public CreatedRefreshSession {

        if (session == null) {
            throw new IllegalArgumentException(
                    "session is required");
        }

        if (refreshToken == null) {
            throw new IllegalArgumentException(
                    "refreshToken is required");
        }
    }
}