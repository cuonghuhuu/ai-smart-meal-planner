package com.smartmealplanner.nutrition.calculation;

import java.math.BigDecimal;

/**
 * A calculated nutrient value or range expressed using a stable nutrient
 * code. It is deliberately independent of the persistence entity.
 */
public record CalculatedNutrientTarget(
        String nutrientCode,
        BigDecimal targetAmount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean hardLimit) {

    public CalculatedNutrientTarget {
        if (nutrientCode == null || nutrientCode.isBlank()) {
            throw new IllegalArgumentException(
                    "nutrientCode is required");
        }

        nutrientCode = nutrientCode.trim();

        if (targetAmount == null
                && minAmount == null
                && maxAmount == null) {

            throw new IllegalArgumentException(
                    "at least one target amount or bound is required");
        }

        if (isNegative(targetAmount)
                || isNegative(minAmount)
                || isNegative(maxAmount)) {

            throw new IllegalArgumentException(
                    "target amounts and bounds must not be negative");
        }

        if (minAmount != null
                && maxAmount != null
                && maxAmount.compareTo(minAmount) < 0) {

            throw new IllegalArgumentException(
                    "maxAmount must not be less than minAmount");
        }

        if (targetAmount != null
                && ((minAmount != null
                        && targetAmount.compareTo(minAmount) < 0)
                    || (maxAmount != null
                        && targetAmount.compareTo(maxAmount) > 0))) {

            throw new IllegalArgumentException(
                    "targetAmount must be within its bounds");
        }
    }

    public boolean isHardLimit() {
        return hardLimit;
    }

    private static boolean isNegative(
            BigDecimal value) {

        return value != null && value.signum() < 0;
    }
}
