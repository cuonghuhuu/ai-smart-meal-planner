package com.smartmealplanner.nutrition.lifecycle;

/**
 * Structured failures raised while validating or writing a target timeline.
 */
public enum NutritionTargetLifecycleFailure {
    INVALID_COMMAND,
    DUPLICATE_NUTRIENT_CODE,
    UNKNOWN_NUTRIENT_CODE,
    SAME_EFFECTIVE_DATE,
    CORRUPTED_TIMELINE,
    OVERLAPPING_TIMELINE,
    PERSISTENCE_FAILURE
}
