package com.smartmealplanner.auth.application;

import java.util.UUID;

import com.smartmealplanner.auth.persistence.AccountStatus;

public record RegistrationResult(
        UUID publicId,
        AccountStatus accountStatus) {
}