package com.smartmealplanner.nutrition.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** HTTP response for a non-persisting deterministic Nutrition preview. */
public record NutritionCalculationResponse(
        LocalDate effectiveFrom,
        String calculationMethod,
        String status,
        String reason,
        NutritionCalculationInputResponse input,
        int age,
        BigDecimal rmrKcal,
        BigDecimal maintenanceEnergyKcal,
        List<NutritionCalculatedValueResponse> nutrientTargets) {
}
