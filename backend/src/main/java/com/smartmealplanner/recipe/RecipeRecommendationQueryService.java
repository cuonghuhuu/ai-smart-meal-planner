package com.smartmealplanner.recipe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.nutrition.application.NutritionReferenceQueryService;
import com.smartmealplanner.nutrition.application.NutritionReferenceSnapshot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipe-owned read boundary used by deterministic recommendation code.
 * It batches all child and reference reads for the candidate set.
 */
@Service
public class RecipeRecommendationQueryService {

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeTagRepository tags;
    private final MealSlotTypeRepository mealSlots;
    private final RecipeNutritionSnapshotRepository snapshots;
    private final RecipeNutritionValueRepository nutritionValues;
    private final IngredientReferenceQueryService ingredientReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;
    private final NutritionReferenceQueryService nutrientReferences;

    public RecipeRecommendationQueryService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeTagRepository tags,
            MealSlotTypeRepository mealSlots,
            RecipeNutritionSnapshotRepository snapshots,
            RecipeNutritionValueRepository nutritionValues,
            IngredientReferenceQueryService ingredientReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            NutritionReferenceQueryService nutrientReferences) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.tags = tags;
        this.mealSlots = mealSlots;
        this.snapshots = snapshots;
        this.nutritionValues = nutritionValues;
        this.ingredientReferences = ingredientReferences;
        this.unitReferences = unitReferences;
        this.nutrientReferences = nutrientReferences;
    }

    @Transactional(readOnly = true)
    public List<RecipeRecommendationCandidate> findPublishedCandidates() {
        List<Recipe> published = recipes.findAllByStatusOrderByTitleAsc(
                RecipeStatus.PUBLISHED);
        if (published.isEmpty()) {
            return List.of();
        }

        Set<Long> recipeIds = new LinkedHashSet<>();
        for (Recipe recipe : published) {
            if (recipe == null || recipe.internalId() == null
                    || recipe.publicId() == null || recipe.title() == null
                    || recipe.servings() == null) {
                throw new IllegalStateException("Recipe candidate data is inconsistent");
            }
            recipeIds.add(recipe.internalId());
        }

        List<RecipeIngredient> lines = ingredients
                .findByRecipeIdsOrderByRecipeIdAscLineNumberAsc(recipeIds);
        Map<Long, List<RecipeIngredient>> linesByRecipe = new LinkedHashMap<>();
        Set<Long> ingredientIds = new LinkedHashSet<>();
        Set<Long> unitIds = new LinkedHashSet<>();
        for (RecipeIngredient line : lines) {
            if (line == null || line.recipeId() == null
                    || !recipeIds.contains(line.recipeId())
                    || line.ingredientId() == null
                    || (line.quantity() == null) != (line.unitId() == null)
                    || (line.quantity() != null && line.quantity().signum() <= 0)) {
                throw new IllegalStateException("Recipe ingredient data is inconsistent");
            }
            linesByRecipe.computeIfAbsent(line.recipeId(), ignored -> new java.util.ArrayList<>())
                    .add(line);
            ingredientIds.add(line.ingredientId());
            if (line.unitId() != null) {
                unitIds.add(line.unitId());
            }
        }

        Map<Long, IngredientReferenceSnapshot> ingredientById = ingredientReferences
                .resolveByInternalIds(ingredientIds);
        Map<Long, MeasurementUnitReferenceSnapshot> unitById = unitReferences
                .resolveByInternalIds(unitIds);
        if (ingredientById.size() != ingredientIds.size()
                || unitById.size() != unitIds.size()) {
            throw new IllegalStateException("Recipe catalog reference data is inconsistent");
        }

        Map<Long, List<RecipeTagAssignmentView>> tagsByRecipe = groupTags(recipeIds);
        Map<Long, List<RecipeMealSlotAssignmentView>> slotsByRecipe = groupMealSlots(recipeIds);
        Map<Long, RecipeNutritionSnapshot> snapshotByRecipe = currentSnapshots(recipeIds);
        Map<Long, Map<String, java.math.BigDecimal>> nutritionByRecipe =
                nutritionByRecipe(snapshotByRecipe);

        return published.stream()
                .map(recipe -> candidate(
                        recipe,
                        linesByRecipe.getOrDefault(recipe.internalId(), List.of()),
                        ingredientById,
                        unitById,
                        tagsByRecipe.getOrDefault(recipe.internalId(), List.of()),
                        slotsByRecipe.getOrDefault(recipe.internalId(), List.of()),
                        nutritionByRecipe.getOrDefault(recipe.internalId(), Map.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, RecipeRecommendationCandidate.MealSlot> resolveMealSlots(
            Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        List<MealSlotType> values = mealSlots.findAllByCodeIn(codes);
        Map<String, RecipeRecommendationCandidate.MealSlot> result = new LinkedHashMap<>();
        for (MealSlotType slot : values) {
            if (slot == null || slot.internalId() == null || slot.code() == null
                    || slot.displayName() == null || slot.displayOrder() == null) {
                throw new IllegalStateException("Meal slot reference data is inconsistent");
            }
            result.put(slot.code(), new RecipeRecommendationCandidate.MealSlot(
                    slot.internalId(), slot.code(), slot.displayName(), slot.displayOrder()));
        }
        return Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public Map<Long, RecipeRecommendationCandidate.MealSlot> resolveMealSlotsByInternalIds(
            Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecipeRecommendationCandidate.MealSlot> result = new LinkedHashMap<>();
        for (MealSlotType slot : mealSlots.findAllById(ids)) {
            if (slot == null || slot.internalId() == null || slot.code() == null
                    || slot.displayName() == null || slot.displayOrder() == null) {
                throw new IllegalStateException("Meal slot reference data is inconsistent");
            }
            result.put(slot.internalId(), new RecipeRecommendationCandidate.MealSlot(
                    slot.internalId(), slot.code(), slot.displayName(), slot.displayOrder()));
        }
        return Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public Map<Long, RecipeRecommendationCandidate.RecipeReference> resolveRecipeReferences(
            Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecipeRecommendationCandidate.RecipeReference> result = new LinkedHashMap<>();
        for (Recipe recipe : recipes.findAllById(ids)) {
            if (recipe == null || recipe.internalId() == null || recipe.publicId() == null
                    || recipe.title() == null) {
                throw new IllegalStateException("Recipe reference data is inconsistent");
            }
            result.put(recipe.internalId(), new RecipeRecommendationCandidate.RecipeReference(
                    recipe.internalId(), recipe.publicId(), recipe.title()));
        }
        return Map.copyOf(result);
    }

    private Map<Long, List<RecipeTagAssignmentView>> groupTags(Set<Long> recipeIds) {
        Map<Long, List<RecipeTagAssignmentView>> grouped = new LinkedHashMap<>();
        for (RecipeTagAssignmentView value : tags.findByRecipeIdsOrderByDisplayNameAscCodeAsc(recipeIds)) {
            if (value == null || value.recipeId() == null || !recipeIds.contains(value.recipeId())
                    || value.code() == null || value.displayName() == null || value.tagKind() == null) {
                throw new IllegalStateException("Recipe tag data is inconsistent");
            }
            grouped.computeIfAbsent(value.recipeId(), ignored -> new java.util.ArrayList<>()).add(value);
        }
        return grouped;
    }

    private Map<Long, List<RecipeMealSlotAssignmentView>> groupMealSlots(Set<Long> recipeIds) {
        Map<Long, List<RecipeMealSlotAssignmentView>> grouped = new LinkedHashMap<>();
        for (RecipeMealSlotAssignmentView value : mealSlots
                .findByRecipeIdsOrderByDisplayOrderAscCodeAsc(recipeIds)) {
            if (value == null || value.recipeId() == null || !recipeIds.contains(value.recipeId())
                    || value.code() == null || value.displayName() == null || value.displayOrder() == null) {
                throw new IllegalStateException("Recipe meal-slot data is inconsistent");
            }
            grouped.computeIfAbsent(value.recipeId(), ignored -> new java.util.ArrayList<>()).add(value);
        }
        return grouped;
    }

    private Map<Long, RecipeNutritionSnapshot> currentSnapshots(Set<Long> recipeIds) {
        Map<Long, RecipeNutritionSnapshot> result = new LinkedHashMap<>();
        for (RecipeNutritionSnapshot snapshot : snapshots.findCurrentByRecipeIdIn(recipeIds)) {
            if (snapshot == null || snapshot.recipeId() == null || snapshot.internalId() == null
                    || !recipeIds.contains(snapshot.recipeId())) {
                throw new IllegalStateException("Recipe nutrition snapshot data is inconsistent");
            }
            if (result.put(snapshot.recipeId(), snapshot) != null) {
                throw new IllegalStateException("Multiple current nutrition snapshots exist");
            }
        }
        return result;
    }

    private Map<Long, Map<String, java.math.BigDecimal>> nutritionByRecipe(
            Map<Long, RecipeNutritionSnapshot> snapshotsByRecipe) {
        if (snapshotsByRecipe.isEmpty()) {
            return Map.of();
        }
        Set<Long> snapshotIds = new LinkedHashSet<>();
        for (RecipeNutritionSnapshot snapshot : snapshotsByRecipe.values()) {
            snapshotIds.add(snapshot.internalId());
        }
        List<RecipeNutritionValue> values = nutritionValues.findBySnapshotIdIn(snapshotIds);
        Set<Long> nutrientIds = new LinkedHashSet<>();
        for (RecipeNutritionValue value : values) {
            if (value == null || value.snapshotId() == null || value.nutrientId() == null
                    || value.amountPerServing() == null || value.amountPerServing().signum() < 0) {
                throw new IllegalStateException("Recipe nutrition value data is inconsistent");
            }
            nutrientIds.add(value.nutrientId());
        }
        Map<Long, NutritionReferenceSnapshot> references = nutrientReferences
                .resolveByInternalIds(nutrientIds);
        if (references.size() != nutrientIds.size()) {
            throw new IllegalStateException("Nutrient reference data is inconsistent");
        }

        Map<Long, Map<String, java.math.BigDecimal>> result = new LinkedHashMap<>();
        for (RecipeNutritionValue value : values) {
            NutritionReferenceSnapshot reference = references.get(value.nutrientId());
            if (reference == null) {
                throw new IllegalStateException("Nutrient reference data is inconsistent");
            }
            Map<String, java.math.BigDecimal> byCode = result.computeIfAbsent(
                    value.snapshotId(), ignored -> new LinkedHashMap<>());
            if (byCode.put(reference.code(), value.amountPerServing()) != null) {
                throw new IllegalStateException("Duplicate recipe nutrient value");
            }
        }

        Map<Long, Map<String, java.math.BigDecimal>> byRecipe = new LinkedHashMap<>();
        for (Map.Entry<Long, RecipeNutritionSnapshot> entry : snapshotsByRecipe.entrySet()) {
            byRecipe.put(entry.getKey(), result.getOrDefault(entry.getValue().internalId(), Map.of()));
        }
        return byRecipe;
    }

    private static RecipeRecommendationCandidate candidate(
            Recipe recipe,
            List<RecipeIngredient> lines,
            Map<Long, IngredientReferenceSnapshot> ingredientById,
            Map<Long, MeasurementUnitReferenceSnapshot> unitById,
            List<RecipeTagAssignmentView> tags,
            List<RecipeMealSlotAssignmentView> slots,
            Map<String, java.math.BigDecimal> nutrition) {

        List<RecipeRecommendationCandidate.Ingredient> ingredientViews = lines.stream()
                .map(line -> {
                    IngredientReferenceSnapshot ingredient = ingredientById.get(line.ingredientId());
                    if (ingredient == null) {
                        throw new IllegalStateException("Recipe ingredient reference is missing");
                    }
                    MeasurementUnitReferenceSnapshot unit = line.unitId() == null
                            ? null : unitById.get(line.unitId());
                    if (line.quantity() != null && unit == null) {
                        throw new IllegalStateException("Recipe ingredient unit reference is missing");
                    }
                    return new RecipeRecommendationCandidate.Ingredient(
                            ingredient.internalId(), ingredient.publicId(), ingredient.code(),
                            line.quantity(), unit == null ? null : unit.code(), line.isOptional());
                })
                .toList();

        List<String> tagCodes = tags.stream().map(RecipeTagAssignmentView::code).toList();
        List<RecipeRecommendationCandidate.MealSlot> mealSlotViews = slots.stream()
                .map(slot -> new RecipeRecommendationCandidate.MealSlot(
                        null, slot.code(), slot.displayName(), slot.displayOrder()))
                .toList();
        return new RecipeRecommendationCandidate(
                recipe.internalId(), recipe.publicId(), recipe.title(), recipe.servings(),
                recipe.totalMinutes(), ingredientViews, tagCodes, mealSlotViews, nutrition);
    }
}
