package com.smartmealplanner.nutrition.application;

import java.time.LocalDate;
import java.util.List;

import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;

/**
 * Public-safe application view of a nutrition target snapshot.
 */
public record NutritionTargetView(
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        NutritionTargetOrigin origin,
        String calculationMethod,
        String activityLevelCode,
        String nutritionGoalCode,
        List<NutritionTargetValueView> nutrientValues) {

    public NutritionTargetView {
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

        if (nutrientValues == null) {
            throw new IllegalArgumentException(
                    "nutrientValues are required");
        }

        nutrientValues = List.copyOf(nutrientValues);
    }
}
