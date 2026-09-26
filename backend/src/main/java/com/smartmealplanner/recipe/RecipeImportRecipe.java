package com.smartmealplanner.recipe;

import java.util.List;

/** One normalized, project-curated Recipe definition. */
public record RecipeImportRecipe(
        String sourceIdentifier,
        String title,
        String slug,
        String summary,
        Integer servings,
        Integer prepMinutes,
        Integer cookMinutes,
        RecipeDifficulty difficulty,
        String instructionsNote,
        String imageUrl,
        RecipeSource source,
        String sourceReference,
        List<String> tags,
        List<String> mealSlots,
        List<RecipeImportIngredient> ingredients,
        List<RecipeImportStep> steps) {

    public RecipeImportRecipe {
        tags = tags == null ? List.of() : List.copyOf(tags);
        mealSlots = mealSlots == null ? List.of() : List.copyOf(mealSlots);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
