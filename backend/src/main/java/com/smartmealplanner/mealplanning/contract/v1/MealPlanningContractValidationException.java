package com.smartmealplanner.mealplanning.contract.v1;

import java.util.List;

/** Raised when an internal meal-planning payload violates contract version 1. */
public final class MealPlanningContractValidationException extends RuntimeException {

    private final List<String> violations;

    MealPlanningContractValidationException(List<String> violations) {
        super("Meal-planning contract validation failed: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
