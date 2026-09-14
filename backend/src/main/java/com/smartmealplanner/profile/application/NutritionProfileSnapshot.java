package com.smartmealplanner.profile.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persistence-neutral profile and measurement snapshot for one effective date.
 * Nullable fields remain nullable so callers can report missing calculation
 * context instead of inventing defaults.
 */
public record NutritionProfileSnapshot(
        LocalDate birthDate,
        String sexCode,
        BigDecimal heightCm,
        Long activityLevelId,
        String activityLevelCode,
        BigDecimal activityFactor,
        Long nutritionGoalId,
        String nutritionGoalCode,
        BigDecimal selectedWeightKg,
        LocalDate selectedWeightMeasuredOn) {
}
