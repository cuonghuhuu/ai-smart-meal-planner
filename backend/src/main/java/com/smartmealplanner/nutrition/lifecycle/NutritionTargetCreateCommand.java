package com.smartmealplanner.nutrition.lifecycle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;

/**
 * Internal prepared-target write command. Effective-to is deliberately
 * derived by the lifecycle service and cannot be supplied by the caller.
 */
public record NutritionTargetCreateCommand(
        Long userId,
        LocalDate effectiveFrom,
        NutritionTargetOrigin origin,
        Long activityLevelId,
        Long nutritionGoalId,
        String calculationMethod,
        String note,
        List<NutritionTargetValueDraft> nutrientValues) {

    public NutritionTargetCreateCommand {
        if (userId == null) {
            throw NutritionTargetLifecycleException.invalidCommand(
                    "userId is required");
        }

        if (effectiveFrom == null) {
            throw NutritionTargetLifecycleException.invalidCommand(
                    "effectiveFrom is required");
        }

        if (origin == null) {
            throw NutritionTargetLifecycleException.invalidCommand(
                    "origin is required");
        }

        if (nutrientValues == null || nutrientValues.isEmpty()) {
            throw NutritionTargetLifecycleException.invalidCommand(
                    "at least one nutrient value is required");
        }

        List<NutritionTargetValueDraft> copiedValues =
                new ArrayList<>(nutrientValues.size());
        Set<String> nutrientCodes = new HashSet<>();

        for (NutritionTargetValueDraft value : nutrientValues) {
            if (value == null) {
                throw NutritionTargetLifecycleException.invalidCommand(
                        "nutrient value is required");
            }

            String duplicateKey = value.nutrientCode()
                    .toUpperCase(Locale.ROOT);

            if (!nutrientCodes.add(duplicateKey)) {
                throw new NutritionTargetLifecycleException(
                        NutritionTargetLifecycleFailure
                                .DUPLICATE_NUTRIENT_CODE,
                        "duplicate nutrient code: "
                                + value.nutrientCode());
            }

            copiedValues.add(value);
        }

        nutrientValues = List.copyOf(copiedValues);
    }
}
