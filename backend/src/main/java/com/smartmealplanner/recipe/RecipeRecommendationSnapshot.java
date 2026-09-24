package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Recipe-owned immutable candidate; no entity or web DTO escapes this boundary. */
public record RecipeRecommendationSnapshot(
        UUID recipePublicId, BigDecimal servings, Integer totalMinutes,
        List<String> mealSlotCodes, List<String> dietaryCodes,
        List<String> tagCodes, List<IngredientRequirement> ingredients,
        Nutrition nutrition) {
    public RecipeRecommendationSnapshot {
        mealSlotCodes = List.copyOf(mealSlotCodes);
        dietaryCodes = List.copyOf(dietaryCodes);
        tagCodes = List.copyOf(tagCodes);
        ingredients = List.copyOf(ingredients);
    }

    public record IngredientRequirement(UUID ingredientPublicId, BigDecimal quantity,
            String unitCode, boolean optional, boolean allowSubstitution) { }

    public record Nutrition(BigDecimal completenessRatio, List<Value> values) {
        public Nutrition { values = List.copyOf(values); }
    }

    public record Value(String nutrientCode, BigDecimal amountPerServing,
            String unitCode) { }
}
