package com.smartmealplanner.recipe;

public record RecipeStepView(
        Integer stepNumber,
        String instruction,
        Integer durationMinutes) {
}
