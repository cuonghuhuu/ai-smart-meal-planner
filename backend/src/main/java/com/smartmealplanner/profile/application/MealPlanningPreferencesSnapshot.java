package com.smartmealplanner.profile.application;

import java.util.List;

/** Immutable Profile-owned hard and soft preference codes. */
public record MealPlanningPreferencesSnapshot(
        List<String> allergenCodes,
        List<String> exclusionaryDietaryCodes,
        List<String> softPreferenceCodes) {
    public MealPlanningPreferencesSnapshot {
        allergenCodes = List.copyOf(allergenCodes);
        exclusionaryDietaryCodes = List.copyOf(exclusionaryDietaryCodes);
        softPreferenceCodes = List.copyOf(softPreferenceCodes);
    }
}
