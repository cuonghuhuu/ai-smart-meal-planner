package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;

/**
 * Persistence-neutral value supplied for a user-defined target.
 */
public record UserDefinedNutritionValueCommand(
        String nutrientCode,
        BigDecimal targetAmount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean hardLimit) {

    public UserDefinedNutritionValueCommand {
        if (nutrientCode == null || nutrientCode.isBlank()) {
            throw NutritionApplicationException.invalidRequest(
                    "nutrientCode is required");
        }

        nutrientCode = nutrientCode.trim();
    }
}
