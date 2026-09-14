package com.smartmealplanner.nutrition.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public calculation inputs selected by the Nutrition application workflow. */
public record NutritionCalculationInputResponse(
        LocalDate birthDate,
        String sex,
        BigDecimal heightCm,
        BigDecimal weightKg,
        LocalDate weightMeasuredOn,
        String activityLevelCode,
        BigDecimal activityFactor,
        String nutritionGoalCode) {
}
