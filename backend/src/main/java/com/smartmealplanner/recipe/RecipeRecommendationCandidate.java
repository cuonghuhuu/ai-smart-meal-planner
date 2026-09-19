package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable Recipe-owned candidate boundary for recommendation and planning.
 * Internal ids are application handles only and never cross into HTTP.
 */
public record RecipeRecommendationCandidate(
        Long internalId,
        UUID publicId,
        String title,
        Short servings,
        Short totalMinutes,
        List<Ingredient> ingredients,
        List<String> tagCodes,
        List<MealSlot> mealSlots,
        Map<String, BigDecimal> nutritionPerServing) {

    public RecipeRecommendationCandidate {
        if (internalId == null || publicId == null || title == null
                || servings == null || ingredients == null || tagCodes == null
                || mealSlots == null || nutritionPerServing == null) {
            throw new IllegalArgumentException("candidate fields are required");
        }
        ingredients = List.copyOf(ingredients);
        tagCodes = List.copyOf(tagCodes);
        mealSlots = List.copyOf(mealSlots);
        nutritionPerServing = Map.copyOf(nutritionPerServing);
    }

    public record Ingredient(
            Long internalId,
            UUID publicId,
            String code,
            BigDecimal quantity,
            String unitCode,
            boolean optional) {
    }

    public record MealSlot(
            Long internalId,
            String code,
            String displayName,
            Short displayOrder) {
    }

    public record RecipeReference(
            Long internalId,
            UUID publicId,
            String title) {
    }
}
