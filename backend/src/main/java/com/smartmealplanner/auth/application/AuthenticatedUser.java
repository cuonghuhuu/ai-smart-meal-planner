package com.smartmealplanner.auth.application;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.UserAccount;

public record AuthenticatedUser(
        UserAccount account,
        UUID publicId,
        List<String> roles) {

    public AuthenticatedUser {

        if (account == null) {
            throw new IllegalArgumentException(
                    "account is required");
        }

        if (publicId == null) {
            throw new IllegalArgumentException(
                    "publicId is required");
        }

        roles = List.copyOf(roles);
    }
}