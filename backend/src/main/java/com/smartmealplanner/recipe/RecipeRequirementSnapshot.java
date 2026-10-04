package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Immutable recipe composition used by persisted-plan derived projections. */
public record RecipeRequirementSnapshot(
        UUID recipePublicId,
        BigDecimal baseServings,
        List<IngredientRequirement> ingredients) {

    public RecipeRequirementSnapshot {
        ingredients = List.copyOf(ingredients);
    }

    public record IngredientRequirement(
            UUID ingredientPublicId,
            String ingredientCode,
            String ingredientDisplayName,
            BigDecimal quantity,
            String unitCode,
            boolean optional) {
    }
}
