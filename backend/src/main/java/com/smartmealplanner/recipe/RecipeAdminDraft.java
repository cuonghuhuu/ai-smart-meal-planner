package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Application command for replacing an administrator-owned draft definition. */
public record RecipeAdminDraft(
        String title,
        String slug,
        String summary,
        Integer servings,
        Integer prepMinutes,
        Integer cookMinutes,
        RecipeDifficulty difficulty,
        String instructionsNote,
        String imageUrl,
        List<Ingredient> ingredients,
        List<Step> steps,
        List<String> tagCodes,
        List<String> mealSlotCodes) {

    public record Ingredient(
            UUID ingredientPublicId,
            BigDecimal quantity,
            String unitCode,
            String preparationNote,
            boolean optional,
            boolean allowSubstitution,
            String sectionLabel) {
    }

    public record Step(
            Integer stepNumber,
            String instruction,
            Integer durationMinutes) {
    }
}
