package com.smartmealplanner.auth.application;

import java.util.List;
import java.util.UUID;

public record CurrentUserResult(
        UUID publicId,
        String email,
        List<String> roles) {

    public CurrentUserResult {

        if (publicId == null) {
            throw new IllegalArgumentException(
                    "publicId is required");
        }

        if (email == null
                || email.isBlank()) {

            throw new IllegalArgumentException(
                    "email is required");
        }

        if (roles == null
                || roles.isEmpty()) {

            throw new IllegalArgumentException(
                    "roles are required");
        }

        roles = List.copyOf(
                roles);
    }
}
