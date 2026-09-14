package com.smartmealplanner.nutrition.lifecycle;

import java.math.BigDecimal;

/**
 * Persistence-neutral nutrient value prepared for a target write.
 */
public record NutritionTargetValueDraft(
        String nutrientCode,
        BigDecimal targetAmount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean hardLimit) {

    public NutritionTargetValueDraft {
        if (nutrientCode == null || nutrientCode.isBlank()) {
            throw NutritionTargetLifecycleException.invalidCommand(
                    "nutrientCode is required");
        }

        nutrientCode = nutrientCode.trim();

        if (targetAmount == null
                && minAmount == null
                && maxAmount == null) {

            throw NutritionTargetLifecycleException.invalidCommand(
                    "at least one target amount or bound is required");
        }

        if (isNegative(targetAmount)
                || isNegative(minAmount)
                || isNegative(maxAmount)) {

            throw NutritionTargetLifecycleException.invalidCommand(
                    "target amounts and bounds must not be negative");
        }

        if (minAmount != null
                && maxAmount != null
                && maxAmount.compareTo(minAmount) < 0) {

            throw NutritionTargetLifecycleException.invalidCommand(
                    "maxAmount must not be less than minAmount");
        }

        if (targetAmount != null
                && ((minAmount != null
                        && targetAmount.compareTo(minAmount) < 0)
                    || (maxAmount != null
                        && targetAmount.compareTo(maxAmount) > 0))) {

            throw NutritionTargetLifecycleException.invalidCommand(
                    "targetAmount must be within its bounds");
        }
    }

    private static boolean isNegative(
            BigDecimal value) {

        return value != null && value.signum() < 0;
    }
}
