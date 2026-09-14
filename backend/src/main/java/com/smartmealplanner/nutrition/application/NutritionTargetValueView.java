package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;

/**
 * Public-safe application view of one normalized nutrient target value.
 */
public record NutritionTargetValueView(
        String nutrientCode,
        String unitCode,
        String unitDisplayName,
        BigDecimal targetAmount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean hardLimit) {
}
