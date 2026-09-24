package com.smartmealplanner.mealplanning.application;

/** No Python stack trace or private snapshot content is included in messages. */
public final class MealPlanningIntegrationException extends RuntimeException {
    private final MealPlanningIntegrationFailure failure;

    public MealPlanningIntegrationException(MealPlanningIntegrationFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public MealPlanningIntegrationException(MealPlanningIntegrationFailure failure,
            Throwable cause) {
        super(failure.name(), cause);
        this.failure = failure;
    }

    public MealPlanningIntegrationFailure failure() { return failure; }
}
