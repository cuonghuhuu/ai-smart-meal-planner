package com.smartmealplanner.nutrition.web;

import java.math.BigDecimal;

/** One calculated Nutrition target value without persistence identifiers. */
public record NutritionCalculatedValueResponse(
        String nutrientCode,
        BigDecimal targetAmount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean hardLimit) {
}
