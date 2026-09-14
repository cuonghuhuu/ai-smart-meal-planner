package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.smartmealplanner.nutrition.calculation.CalculatedNutrientTarget;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationReason;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationStatus;

/**
 * Application result for a non-persisting deterministic calculation preview.
 */
public record NutritionCalculationPreviewResult(
        LocalDate effectiveFrom,
        String calculationMethod,
        NutritionCalculationStatus status,
        NutritionCalculationReason reason,
        NutritionCalculationInputSnapshot input,
        int age,
        BigDecimal rmrKcal,
        BigDecimal maintenanceEnergyKcal,
        List<CalculatedNutrientTarget> nutrientTargets) {

    public NutritionCalculationPreviewResult {
        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "effectiveFrom is required");
        }

        if (calculationMethod == null
                || calculationMethod.isBlank()) {

            throw new IllegalArgumentException(
                    "calculationMethod is required");
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "status is required");
        }

        if (input == null) {
            throw new IllegalArgumentException(
                    "input is required");
        }

        if (age < 0) {
            throw new IllegalArgumentException(
                    "age must not be negative");
        }

        if (nutrientTargets == null) {
            throw new IllegalArgumentException(
                    "nutrientTargets are required");
        }

        calculationMethod = calculationMethod.trim();
        nutrientTargets = List.copyOf(nutrientTargets);
    }
}
