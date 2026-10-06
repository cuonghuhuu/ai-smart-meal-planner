package com.smartmealplanner.recommendation.application;

/** Structured failures for the personalized Recipe recommendation workflow. */
public enum RecipeRecommendationFailure {
    INVALID_INPUT,
    INVALID_TIME_ZONE,
    SNAPSHOT_LIMIT_EXCEEDED,
    SNAPSHOT_INCONSISTENT,
    AI_UNAVAILABLE,
    AI_INVALID_RESPONSE,
    PERSISTENCE_FAILURE
}
