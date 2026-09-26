package com.smartmealplanner.mealplanning.application;

/** Stable application categories, not public HTTP mappings or algorithm outcomes. */
public enum MealPlanningIntegrationFailure {
    INVALID_GENERATION_INPUT,
    SNAPSHOT_LIMIT_EXCEEDED,
    SNAPSHOT_INCONSISTENT,
    TRANSACTION_BOUNDARY_VIOLATION,
    AI_SERVICE_UNAVAILABLE,
    AI_SERVICE_TIMEOUT,
    AI_BAD_RESPONSE,
    AI_SEARCH_BUDGET_EXHAUSTED
}
