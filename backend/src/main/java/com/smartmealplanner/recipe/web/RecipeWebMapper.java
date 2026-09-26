package com.smartmealplanner.recipe.web;

import com.smartmealplanner.recipe.RecipeCatalogPage;
import com.smartmealplanner.recipe.RecipeDetailView;
import com.smartmealplanner.recipe.RecipeIngredientView;
import com.smartmealplanner.recipe.RecipeMealSlotView;
import com.smartmealplanner.recipe.RecipeNutritionSnapshotView;
import com.smartmealplanner.recipe.RecipeNutritionValueView;
import com.smartmealplanner.recipe.RecipeStepView;
import com.smartmealplanner.recipe.RecipeSummaryView;
import com.smartmealplanner.recipe.RecipeTagView;

final class RecipeWebMapper {

    private RecipeWebMapper() {
    }

    static RecipeResponse.Page page(RecipeCatalogPage value) {
        return new RecipeResponse.Page(
                value.page(),
                value.size(),
                value.totalElements(),
                value.totalPages(),
                value.content().stream()
                        .map(RecipeWebMapper::item)
                        .toList());
    }

    static RecipeResponse.Detail detail(RecipeDetailView value) {
        return new RecipeResponse.Detail(
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
                value.ingredients().stream()
                        .map(RecipeWebMapper::ingredient)
                        .toList(),
                value.steps().stream().map(RecipeWebMapper::step).toList(),
                value.tags().stream().map(RecipeWebMapper::tag).toList(),
                value.mealSlots().stream()
                        .map(RecipeWebMapper::mealSlot)
                        .toList(),
                value.nutrition() == null
                        ? null
                        : nutrition(value.nutrition()));
    }

    static RecipeResponse.Tag tag(RecipeTagView value) {
        return new RecipeResponse.Tag(
                value.code(), value.displayName(), value.tagKind());
    }

    static RecipeResponse.MealSlot mealSlot(RecipeMealSlotView value) {
        return new RecipeResponse.MealSlot(
                value.code(),
                value.displayName(),
                value.displayOrder(),
                value.typicalTime(),
                value.mainMeal());
    }

    private static RecipeResponse.Item item(RecipeSummaryView value) {
        return new RecipeResponse.Item(
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
                value.tags().stream().map(RecipeWebMapper::tag).toList(),
                value.mealSlots().stream()
                        .map(RecipeWebMapper::mealSlot)
                        .toList());
    }

    private static RecipeResponse.Ingredient ingredient(
            RecipeIngredientView value) {
        return new RecipeResponse.Ingredient(
                value.lineNumber(),
                value.ingredientPublicId(),
                value.ingredientCode(),
                value.ingredientDisplayName(),
                value.foodPublicId(),
                value.foodCode(),
                value.foodDisplayName(),
                value.quantity(),
                value.unitCode(),
                value.unitDisplayName(),
                value.preparationNote(),
                value.optional(),
                value.allowSubstitution(),
                value.sectionLabel());
    }

    private static RecipeResponse.Step step(RecipeStepView value) {
        return new RecipeResponse.Step(
                value.stepNumber(), value.instruction(), value.durationMinutes());
    }

    private static RecipeResponse.NutritionSnapshot nutrition(
            RecipeNutritionSnapshotView value) {
        return new RecipeResponse.NutritionSnapshot(
                value.computedAt(),
                value.ingredientRevision(),
                value.completenessRatio(),
                value.computationNote(),
                value.values().stream()
                        .map(RecipeWebMapper::nutritionValue)
                        .toList());
    }

    private static RecipeResponse.NutritionValue nutritionValue(
            RecipeNutritionValueView value) {
        return new RecipeResponse.NutritionValue(
                value.nutrientCode(),
                value.nutrientDisplayName(),
                value.amountPerServing(),
                value.unitCode(),
                value.unitDisplayName());
    }
}
