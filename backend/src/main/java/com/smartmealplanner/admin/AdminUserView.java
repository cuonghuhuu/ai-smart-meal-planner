package com.smartmealplanner.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.AccountStatus;

public record AdminUserView(
        UUID publicId,
        String email,
        String displayName,
        AccountStatus accountStatus,
        List<String> roles,
        LocalDateTime emailVerifiedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}
