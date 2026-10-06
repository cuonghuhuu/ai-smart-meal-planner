package com.smartmealplanner.recommendation.application;

/** Application-level recommendation failure with a stable machine-readable reason. */
public final class RecipeRecommendationException extends RuntimeException {
    private final RecipeRecommendationFailure failure;

    public RecipeRecommendationException(RecipeRecommendationFailure failure) {
        super(failure == null ? "Recipe recommendation failed" : failure.name());
        if (failure == null) {
            throw new IllegalArgumentException("failure is required");
        }
        this.failure = failure;
    }

    public RecipeRecommendationException(
            RecipeRecommendationFailure failure, Throwable cause) {
        super(failure == null ? "Recipe recommendation failed" : failure.name(), cause);
        if (failure == null) {
            throw new IllegalArgumentException("failure is required");
        }
        this.failure = failure;
    }

    public RecipeRecommendationFailure failure() {
        return failure;
    }
}
