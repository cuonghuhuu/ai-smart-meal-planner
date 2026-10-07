package com.smartmealplanner.admin;

import java.util.List;

public record AdminUserPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<AdminUserView> content) {
}
