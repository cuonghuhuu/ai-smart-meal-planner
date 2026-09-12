package com.smartmealplanner.profile.web;

public record NutritionGoalItemResponse(
        String code,
        String displayName,
        String description,
        int displayOrder) {
}
