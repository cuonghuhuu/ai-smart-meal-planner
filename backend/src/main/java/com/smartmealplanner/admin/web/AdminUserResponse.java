package com.smartmealplanner.admin.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.admin.AdminUserPage;
import com.smartmealplanner.admin.AdminUserView;
import com.smartmealplanner.auth.persistence.AccountStatus;

public final class AdminUserResponse {

    private AdminUserResponse() {
    }

    public record Item(
            UUID publicId,
            String email,
            String displayName,
            AccountStatus accountStatus,
            List<String> roles,
            LocalDateTime emailVerifiedAt,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt) {
    }

    public record Page(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<Item> content) {
    }

    public record StatusRequest(String status) {
    }

    static Page page(AdminUserPage value) {
        return new Page(
                value.page(),
                value.size(),
                value.totalElements(),
                value.totalPages(),
                value.content().stream()
                        .map(AdminUserResponse::item)
                        .toList());
    }

    static Item item(AdminUserView value) {
        return new Item(
                value.publicId(),
                value.email(),
                value.displayName(),
                value.accountStatus(),
                value.roles(),
                value.emailVerifiedAt(),
                value.lastLoginAt(),
                value.createdAt());
    }
}
