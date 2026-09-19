package com.smartmealplanner.admin.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.recipe.RecipeAdminDraft;
import com.smartmealplanner.recipe.RecipeDifficulty;

public record AdminRecipeRequest(
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

    public RecipeAdminDraft toCommand() {
        return new RecipeAdminDraft(
                title,
                slug,
                summary,
                servings,
                prepMinutes,
                cookMinutes,
                difficulty,
                instructionsNote,
                imageUrl,
                ingredients == null
                        ? null
                        : ingredients.stream().map(Ingredient::toCommand).toList(),
                steps == null
                        ? null
                        : steps.stream().map(Step::toCommand).toList(),
                tagCodes,
                mealSlotCodes);
    }

    public record Ingredient(
            UUID ingredientPublicId,
            BigDecimal quantity,
            String unitCode,
            String preparationNote,
            boolean optional,
            boolean allowSubstitution,
            String sectionLabel) {

        RecipeAdminDraft.Ingredient toCommand() {
            return new RecipeAdminDraft.Ingredient(
                    ingredientPublicId,
                    quantity,
                    unitCode,
                    preparationNote,
                    optional,
                    allowSubstitution,
                    sectionLabel);
        }
    }

    public record Step(
            Integer stepNumber,
            String instruction,
            Integer durationMinutes) {

        RecipeAdminDraft.Step toCommand() {
            return new RecipeAdminDraft.Step(
                    stepNumber, instruction, durationMinutes);
        }
    }
}
