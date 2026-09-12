package com.smartmealplanner.profile.web;

public record UserDietaryPreferenceResponse(
        String code,
        String displayName,
        String description,
        boolean isExclusionary) {
}
