package com.smartmealplanner.admin.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.recipe.RecipeDifficulty;
import com.smartmealplanner.recipe.RecipeMealSlotView;
import com.smartmealplanner.recipe.RecipeNutritionSnapshotView;
import com.smartmealplanner.recipe.RecipeNutritionValueView;
import com.smartmealplanner.recipe.RecipeSource;
import com.smartmealplanner.recipe.RecipeStatus;
import com.smartmealplanner.recipe.RecipeAdminPage;
import com.smartmealplanner.recipe.RecipeAdminSummaryView;
import com.smartmealplanner.recipe.RecipeDetailView;
import com.smartmealplanner.recipe.RecipeIngredientView;
import com.smartmealplanner.recipe.RecipeStepView;
import com.smartmealplanner.recipe.RecipeTagView;
import com.smartmealplanner.recipe.web.RecipeResponse;

public final class AdminRecipeResponse {

    private AdminRecipeResponse() {
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
            RecipeStatus status,
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
            LocalDateTime archivedAt,
            List<RecipeResponse.Ingredient> ingredients,
            List<RecipeResponse.Step> steps,
            List<Tag> tags,
            List<MealSlot> mealSlots,
            RecipeResponse.NutritionSnapshot nutrition) {
    }

    public record Tag(String code, String displayName, com.smartmealplanner.recipe.RecipeTagKind tagKind) {
    }

    public record MealSlot(
            String code,
            String displayName,
            Short displayOrder,
            java.time.LocalTime typicalTime,
            boolean mainMeal) {
    }

    static Page page(RecipeAdminPage value) {
        return new Page(
                value.page(),
                value.size(),
                value.totalElements(),
                value.totalPages(),
                value.content().stream().map(AdminRecipeResponse::item).toList());
    }

    static Item item(RecipeAdminSummaryView value) {
        return new Item(
                value.publicId(),
                value.title(),
                value.summary(),
                value.servings(),
                value.prepMinutes(),
                value.cookMinutes(),
                value.totalMinutes(),
                value.difficulty(),
                value.imageUrl(),
                value.source(),
                value.status(),
                value.tags().stream().map(AdminRecipeResponse::tag).toList(),
                value.mealSlots().stream().map(AdminRecipeResponse::mealSlot).toList());
    }

    static Detail detail(RecipeDetailView value) {
        return new Detail(
                value.publicId(),
                value.title(),
                value.slug(),
                value.summary(),
                value.servings(),
                value.prepMinutes(),
                value.cookMinutes(),
                value.totalMinutes(),
                value.difficulty(),
                value.instructionsNote(),
                value.imageUrl(),
                value.source(),
                value.sourceReference(),
                value.status(),
                value.publishedAt(),
                value.archivedAt(),
                value.ingredients().stream().map(AdminRecipeResponse::ingredient).toList(),
                value.steps().stream().map(AdminRecipeResponse::step).toList(),
                value.tags().stream().map(AdminRecipeResponse::tag).toList(),
                value.mealSlots().stream().map(AdminRecipeResponse::mealSlot).toList(),
                value.nutrition() == null ? null : nutrition(value.nutrition()));
    }

    private static RecipeResponse.Ingredient ingredient(RecipeIngredientView value) {
        return new RecipeResponse.Ingredient(
                value.lineNumber(), value.ingredientPublicId(), value.ingredientCode(),
                value.ingredientDisplayName(), value.foodPublicId(), value.foodCode(),
                value.foodDisplayName(), value.quantity(), value.unitCode(),
                value.unitDisplayName(), value.preparationNote(), value.optional(),
                value.allowSubstitution(), value.sectionLabel());
    }

    private static RecipeResponse.Step step(RecipeStepView value) {
        return new RecipeResponse.Step(
                value.stepNumber(), value.instruction(), value.durationMinutes());
    }

    private static Tag tag(RecipeTagView value) {
        return new Tag(value.code(), value.displayName(), value.tagKind());
    }

    private static MealSlot mealSlot(RecipeMealSlotView value) {
        return new MealSlot(
                value.code(), value.displayName(), value.displayOrder(),
                value.typicalTime(), value.mainMeal());
    }

    private static RecipeResponse.NutritionSnapshot nutrition(
            RecipeNutritionSnapshotView value) {
        return new RecipeResponse.NutritionSnapshot(
                value.computedAt(), value.ingredientRevision(), value.completenessRatio(),
                value.computationNote(), value.values().stream()
                        .map(AdminRecipeResponse::nutritionValue)
                        .toList());
    }

    private static RecipeResponse.NutritionValue nutritionValue(
            RecipeNutritionValueView value) {
        return new RecipeResponse.NutritionValue(
                value.nutrientCode(), value.nutrientDisplayName(),
                value.amountPerServing(), value.unitCode(), value.unitDisplayName());
    }
}
