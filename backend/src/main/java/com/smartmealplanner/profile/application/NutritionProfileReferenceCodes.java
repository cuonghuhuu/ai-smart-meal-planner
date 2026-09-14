package com.smartmealplanner.profile.application;

import java.util.Map;

/**
 * Internal, persistence-neutral lookup result for target reference snapshots.
 * The identifiers are used only between application modules and are never a
 * public response contract.
 */
public record NutritionProfileReferenceCodes(
        Map<Long, String> activityLevelCodes,
        Map<Long, String> nutritionGoalCodes) {

    public NutritionProfileReferenceCodes {
        if (activityLevelCodes == null) {
            throw new IllegalArgumentException(
                    "activityLevelCodes are required");
        }

        if (nutritionGoalCodes == null) {
            throw new IllegalArgumentException(
                    "nutritionGoalCodes are required");
        }

        activityLevelCodes = Map.copyOf(activityLevelCodes);
        nutritionGoalCodes = Map.copyOf(nutritionGoalCodes);
    }
}
