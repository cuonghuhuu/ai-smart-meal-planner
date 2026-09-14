package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.smartmealplanner.nutrition.calculation.NutritionCalculationSex;

/**
 * Public-safe, persistence-neutral inputs selected for a calculation preview.
 */
public record NutritionCalculationInputSnapshot(
        LocalDate birthDate,
        NutritionCalculationSex sex,
        BigDecimal heightCm,
        BigDecimal weightKg,
        LocalDate weightMeasuredOn,
        String activityLevelCode,
        BigDecimal activityFactor,
        String nutritionGoalCode) {
}
