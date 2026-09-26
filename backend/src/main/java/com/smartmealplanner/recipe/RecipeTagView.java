package com.smartmealplanner.recipe;

public record RecipeTagView(
        String code,
        String displayName,
        RecipeTagKind tagKind) {
}
