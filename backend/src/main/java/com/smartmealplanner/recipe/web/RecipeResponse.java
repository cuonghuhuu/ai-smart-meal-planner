package com.smartmealplanner.recipe.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.recipe.RecipeDifficulty;
import com.smartmealplanner.recipe.RecipeSource;
import com.smartmealplanner.recipe.RecipeStatus;
import com.smartmealplanner.recipe.RecipeTagKind;

/** Public, surrogate-ID-free HTTP representations for Recipe reads. */
public final class RecipeResponse {

    private RecipeResponse() {
    }

    public record Item(
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
            List<Tag> tags,
            List<MealSlot> mealSlots) {
    }

    public record Page(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<Item> content) {
    }

    public record Tag(
            String code,
            String displayName,
            RecipeTagKind tagKind) {
    }

    public record MealSlot(
            String code,
            String displayName,
            Short displayOrder,
            LocalTime typicalTime,
            boolean mainMeal) {
    }

    public record Ingredient(
            Integer lineNumber,
            UUID ingredientPublicId,
            String ingredientCode,
            String ingredientDisplayName,
            UUID foodPublicId,
            String foodCode,
            String foodDisplayName,
            BigDecimal quantity,
            String unitCode,
            String unitDisplayName,
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

    public record NutritionValue(
            String nutrientCode,
            String nutrientDisplayName,
            BigDecimal amountPerServing,
            String unitCode,
            String unitDisplayName) {
    }

    public record NutritionSnapshot(
            LocalDateTime computedAt,
            Integer ingredientRevision,
            BigDecimal completenessRatio,
            String computationNote,
            List<NutritionValue> values) {
    }

    public record Detail(
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
            List<Ingredient> ingredients,
            List<Step> steps,
            List<Tag> tags,
            List<MealSlot> mealSlots,
            NutritionSnapshot nutrition) {
    }
}
