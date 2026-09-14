package com.smartmealplanner.auth.application;

import java.util.UUID;

public record CurrentUserIdentity(
        Long internalId,
        UUID publicId,
        String timeZone) {

    public CurrentUserIdentity {

        if (internalId == null) {
            throw new IllegalArgumentException(
                    "internalId is required");
        }

        if (publicId == null) {
            throw new IllegalArgumentException(
                    "publicId is required");
        }

        if (timeZone == null
                || timeZone.isBlank()) {

            throw new IllegalArgumentException(
                    "timeZone is required");
        }
    }
}
