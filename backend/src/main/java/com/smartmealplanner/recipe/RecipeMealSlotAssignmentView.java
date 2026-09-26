package com.smartmealplanner.recipe;

import java.time.LocalTime;

/** Internal page-level projection of a recipe meal-slot assignment. */
public record RecipeMealSlotAssignmentView(
        Long recipeId,
        String code,
        String displayName,
        Short displayOrder,
        LocalTime typicalTime,
        boolean mainMeal) {
}
