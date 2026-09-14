package com.smartmealplanner.nutrition.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Explicit outcome of a deterministic nutrition calculation.
 */
public record NutritionCalculationResult(
        String calculationMethod,
        NutritionCalculationStatus status,
        NutritionCalculationReason reason,
        int age,
        BigDecimal rmrKcal,
        BigDecimal maintenanceEnergyKcal,
        List<CalculatedNutrientTarget> nutrientTargets) {

    public NutritionCalculationResult {
        if (calculationMethod == null
                || calculationMethod.isBlank()) {

            throw new IllegalArgumentException(
                    "calculationMethod is required");
        }

        calculationMethod = calculationMethod.trim();

        if (status == null) {
            throw new IllegalArgumentException(
                    "status is required");
        }

        if (age < 0) {
            throw new IllegalArgumentException(
                    "age must not be negative");
        }

        if (nutrientTargets == null) {
            throw new IllegalArgumentException(
                    "nutrientTargets are required");
        }

        nutrientTargets = List.copyOf(nutrientTargets);

        switch (status) {
            case TARGET_AVAILABLE -> {
                requireEnergyValues(
                        rmrKcal,
                        maintenanceEnergyKcal);

                if (reason != null) {
                    throw new IllegalArgumentException(
                            "TARGET_AVAILABLE must not have a reason");
                }

                if (nutrientTargets.isEmpty()) {
                    throw new IllegalArgumentException(
                            "TARGET_AVAILABLE requires nutrient targets");
                }
            }
            case BASELINE_ONLY -> {
                requireEnergyValues(
                        rmrKcal,
                        maintenanceEnergyKcal);

                if (reason
                        != NutritionCalculationReason.GOAL_ADJUSTMENT_NOT_SUPPORTED) {

                    throw new IllegalArgumentException(
                            "BASELINE_ONLY requires the goal adjustment reason");
                }

                if (!nutrientTargets.isEmpty()) {
                    throw new IllegalArgumentException(
                            "BASELINE_ONLY must not have nutrient targets");
                }
            }
            case UNSUPPORTED -> {
                if (reason == null) {
                    throw new IllegalArgumentException(
                            "UNSUPPORTED requires a reason");
                }

                if (reason
                        == NutritionCalculationReason.GOAL_ADJUSTMENT_NOT_SUPPORTED) {

                    throw new IllegalArgumentException(
                            "UNSUPPORTED must not use the goal adjustment reason");
                }

                if (rmrKcal != null
                        || maintenanceEnergyKcal != null) {

                    throw new IllegalArgumentException(
                            "UNSUPPORTED must not have calculated energy values");
                }

                if (!nutrientTargets.isEmpty()) {
                    throw new IllegalArgumentException(
                            "UNSUPPORTED must not have nutrient targets");
                }
            }
        }
    }

    public boolean hasPersistableTargetValues() {
        return status == NutritionCalculationStatus.TARGET_AVAILABLE;
    }

    private static void requireEnergyValues(
            BigDecimal rmrKcal,
            BigDecimal maintenanceEnergyKcal) {

        if (rmrKcal == null
                || maintenanceEnergyKcal == null) {

            throw new IllegalArgumentException(
                    "supported calculation requires energy values");
        }
    }
}
