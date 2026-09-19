package com.smartmealplanner.recipe;

/** One ordered, independently written preparation instruction. */
public record RecipeImportStep(
        Integer stepNumber,
        String instruction,
        Integer durationMinutes) {
}
