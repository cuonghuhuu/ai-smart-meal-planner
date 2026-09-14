package com.smartmealplanner.nutrition.web;

import java.math.BigDecimal;

/** Public normalized nutrient value for a persisted Nutrition target. */
public record NutritionTargetValueResponse(
        String nutrientCode,
        String unitCode,
        String unitDisplayName,
        BigDecimal targetAmount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean hardLimit) {
}
