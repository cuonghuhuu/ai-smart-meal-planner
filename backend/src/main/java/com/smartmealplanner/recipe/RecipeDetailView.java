package com.smartmealplanner.recipe;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RecipeDetailView(
        UUID publicId,
        String title,
        String slug,
        String summary,
        Integer servings,
        Integer prepMinutes,
        Integer cookMinutes,
        Integer totalMinutes,
        RecipeDifficulty difficulty,
        String instructionsNote,
        String imageUrl,
        RecipeSource source,
        String sourceReference,
        RecipeStatus status,
        LocalDateTime publishedAt,
        List<RecipeIngredientView> ingredients,
        List<RecipeStepView> steps,
        List<RecipeTagView> tags,
        List<RecipeMealSlotView> mealSlots,
        RecipeNutritionSnapshotView nutrition) {
}
