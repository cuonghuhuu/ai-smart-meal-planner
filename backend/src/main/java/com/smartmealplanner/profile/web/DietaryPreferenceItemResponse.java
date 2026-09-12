package com.smartmealplanner.profile.web;

public record DietaryPreferenceItemResponse(
        String code,
        String displayName,
        String description,
        boolean isExclusionary,
        int displayOrder) {
}
