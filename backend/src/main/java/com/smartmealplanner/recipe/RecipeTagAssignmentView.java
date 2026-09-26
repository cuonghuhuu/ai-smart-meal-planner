package com.smartmealplanner.recipe;

/** Internal page-level projection of a recipe tag assignment. */
public record RecipeTagAssignmentView(
        Long recipeId,
        String code,
        String displayName,
        RecipeTagKind tagKind) {
}
