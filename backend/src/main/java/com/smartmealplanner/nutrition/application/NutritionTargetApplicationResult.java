package com.smartmealplanner.nutrition.application;

import java.time.LocalDate;

import com.smartmealplanner.nutrition.calculation.NutritionCalculationReason;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationStatus;
import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;

/**
 * Public-safe application result for a target write.
 */
public record NutritionTargetApplicationResult(
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        NutritionTargetOrigin origin,
        String calculationMethod,
        NutritionCalculationStatus calculationStatus,
        NutritionCalculationReason calculationReason) {

    public NutritionTargetApplicationResult {
        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "effectiveFrom is required");
        }

        if (effectiveTo != null
                && effectiveTo.isBefore(effectiveFrom)) {

            throw new IllegalArgumentException(
                    "effectiveTo must not be before effectiveFrom");
        }

        if (origin == null) {
            throw new IllegalArgumentException(
                    "origin is required");
        }

        if (calculationMethod != null) {
            calculationMethod = calculationMethod.trim();
        }
    }
}
