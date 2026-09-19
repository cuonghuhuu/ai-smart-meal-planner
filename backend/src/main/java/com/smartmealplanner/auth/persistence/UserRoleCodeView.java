package com.smartmealplanner.auth.persistence;

/** Batch projection used by administrative user listings. */
public record UserRoleCodeView(
        Long userId,
        String code) {
}
