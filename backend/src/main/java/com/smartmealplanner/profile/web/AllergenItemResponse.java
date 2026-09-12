package com.smartmealplanner.profile.web;

public record AllergenItemResponse(
        String code,
        String displayName,
        String description,
        int displayOrder) {
}
