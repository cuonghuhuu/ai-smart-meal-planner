package com.smartmealplanner.mealplanning;

public class MealPlanException extends RuntimeException {
    private final MealPlanFailure failure;

    public MealPlanException(MealPlanFailure failure) {
        super();
        this.failure = failure;
    }

    public MealPlanFailure failure() {
        return failure;
    }
}
