package com.smartmealplanner.food;

/** Immutable Food-owned allergen fact for recommendation constraints. */
public record IngredientAllergenSnapshot(
        Long ingredientInternalId,
        Long allergenId,
        IngredientAllergenPresence presence) {
}
