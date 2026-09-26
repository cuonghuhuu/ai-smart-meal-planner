package com.smartmealplanner.recipe;

import java.util.List;
import java.util.UUID;

public record RecipeSummaryView(
        UUID publicId,
        String title,
        String summary,
        Integer servings,
        Integer prepMinutes,
        Integer cookMinutes,
        Integer totalMinutes,
        RecipeDifficulty difficulty,
        String imageUrl,
        RecipeSource source,
        List<RecipeTagView> tags,
        List<RecipeMealSlotView> mealSlots) {
}
