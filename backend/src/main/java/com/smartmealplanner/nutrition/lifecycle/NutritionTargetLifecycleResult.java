package com.smartmealplanner.nutrition.lifecycle;

import java.time.LocalDate;

/**
 * Minimal internal result of a successful target timeline write.
 */
public record NutritionTargetLifecycleResult(
        Long targetId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {

    public NutritionTargetLifecycleResult {
        if (targetId == null) {
            throw new IllegalArgumentException(
                    "targetId is required");
        }

        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "effectiveFrom is required");
        }

        if (effectiveTo != null
                && effectiveTo.isBefore(effectiveFrom)) {

            throw new IllegalArgumentException(
                    "effectiveTo must not be before effectiveFrom");
        }
    }
}
