package com.smartmealplanner.nutrition.application;

/**
 * Structured application-level outcomes for Nutrition workflows and reads.
 */
public enum NutritionApplicationFailure {
    PROFILE_NOT_FOUND,
    MISSING_CALCULATION_INPUT,
    MEASUREMENT_NOT_FOUND,
    CALCULATION_NOT_SUPPORTED,
    GOAL_ADJUSTMENT_NOT_SUPPORTED,
    INVALID_TIME_ZONE,
    NO_CURRENT_TARGET,
    CORRUPTED_TARGET_TIMELINE,
    CORRUPTED_TARGET_DATA,
    INVALID_REQUEST,
    UNKNOWN_NUTRIENT_CODE,
    DUPLICATE_NUTRIENT_CODE,
    INVALID_TARGET_VALUE,
    SAME_EFFECTIVE_DATE,
    PERSISTENCE_FAILURE
}
