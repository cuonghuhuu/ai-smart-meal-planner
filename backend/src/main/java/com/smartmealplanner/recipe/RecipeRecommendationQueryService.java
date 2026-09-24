package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.nutrition.application.NutritionReferenceQueryService;
import com.smartmealplanner.nutrition.application.NutritionReferenceSnapshot;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deterministic published-only preselection, then bounded batch snapshot reads. */
@Service
public class RecipeRecommendationQueryService {
    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeTagRepository tags;
    private final MealSlotTypeRepository slots;
    private final RecipeNutritionSnapshotRepository nutrition;
    private final RecipeNutritionValueRepository values;
    private final IngredientReferenceQueryService ingredientReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;
    private final NutritionReferenceQueryService nutrientReferences;

    public RecipeRecommendationQueryService(RecipeRepository recipes,
            RecipeIngredientRepository ingredients, RecipeTagRepository tags,
            MealSlotTypeRepository slots, RecipeNutritionSnapshotRepository nutrition,
            RecipeNutritionValueRepository values,
            IngredientReferenceQueryService ingredientReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            NutritionReferenceQueryService nutrientReferences) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.tags = tags;
        this.slots = slots;
        this.nutrition = nutrition;
        this.values = values;
        this.ingredientReferences = ingredientReferences;
        this.unitReferences = unitReferences;
        this.nutrientReferences = nutrientReferences;
    }

    /** Newest published first, public UUID ascending on ties; not AI ranking. */
    @Transactional(readOnly = true)
    public List<RecipeRecommendationSnapshot> candidates(
            List<String> requestedSlotCodes, int maximumCandidates) {
        if (requestedSlotCodes == null || requestedSlotCodes.isEmpty()
                || maximumCandidates < 1 || maximumCandidates > 200) {
            throw new IllegalArgumentException("Invalid recipe candidate bounds");
        }
        List<Recipe> selected = recipes.findRecommendationCandidates(
                requestedSlotCodes, PageRequest.of(0, maximumCandidates));
        if (selected.isEmpty()) { return List.of(); }
        Set<Long> ids = new HashSet<>();
        for (Recipe recipe : selected) {
            if (recipe == null || recipe.internalId() == null
                    || recipe.publicId() == null || recipe.servings() == null
                    || !ids.add(recipe.internalId())) { throw inconsistent(); }
        }
        List<RecipeIngredient> lines = ingredients.findByRecipeIds(ids);
        Map<Long, List<RecipeIngredient>> linesByRecipe = new HashMap<>();
        Set<Long> ingredientIds = new HashSet<>();
        Set<Long> unitIds = new HashSet<>();
        for (RecipeIngredient line : lines) {
            if (line == null || !ids.contains(line.recipeId()) || line.ingredientId() == null) {
                throw inconsistent();
            }
            linesByRecipe.computeIfAbsent(line.recipeId(), ignored -> new ArrayList<>()).add(line);
            ingredientIds.add(line.ingredientId());
            if (line.unitId() != null) { unitIds.add(line.unitId()); }
        }
        Map<Long, IngredientReferenceSnapshot> ingredientRefs =
                ingredientReferences.resolveByInternalIds(ingredientIds);
        Map<Long, MeasurementUnitReferenceSnapshot> unitRefs =
                unitReferences.resolveByInternalIds(unitIds);
        Map<Long, List<RecipeTagAssignmentView>> tagsByRecipe = groupTags(
                tags.findByRecipeIdsOrderByDisplayNameAscCodeAsc(ids));
        Map<Long, List<RecipeMealSlotAssignmentView>> slotsByRecipe = groupSlots(
                slots.findByRecipeIdsOrderByDisplayOrderAscCodeAsc(ids));
        Map<Long, RecipeNutritionSnapshot> snapshots = new HashMap<>();
        for (RecipeNutritionSnapshot snapshot : nutrition.findCurrentByRecipeIds(ids)) {
            if (snapshot == null || snapshot.internalId() == null
                    || !ids.contains(snapshot.recipeId())
                    || snapshots.putIfAbsent(snapshot.recipeId(), snapshot) != null) {
                throw inconsistent();
            }
        }
        Set<Long> snapshotIds = new HashSet<>();
        snapshots.values().forEach(snapshot -> snapshotIds.add(snapshot.internalId()));
        List<RecipeNutritionValue> nutrientValues = snapshotIds.isEmpty()
                ? List.of() : values.findBySnapshotIds(snapshotIds);
        Map<Long, NutritionReferenceSnapshot> nutrientRefs = nutrientReferences.resolveByInternalIds(
                nutrientValues.stream().map(RecipeNutritionValue::nutrientId).collect(
                        java.util.stream.Collectors.toSet()));
        Map<Long, List<RecipeNutritionValue>> valuesBySnapshot = new HashMap<>();
        for (RecipeNutritionValue value : nutrientValues) {
            if (value == null || !snapshotIds.contains(value.snapshotId())) { throw inconsistent(); }
            valuesBySnapshot.computeIfAbsent(value.snapshotId(), ignored -> new ArrayList<>())
                    .add(value);
        }
        return selected.stream().map(recipe -> toSnapshot(recipe, requestedSlotCodes,
                linesByRecipe.getOrDefault(recipe.internalId(), List.of()), ingredientRefs,
                unitRefs, tagsByRecipe.getOrDefault(recipe.internalId(), List.of()),
                slotsByRecipe.getOrDefault(recipe.internalId(), List.of()),
                snapshots.get(recipe.internalId()), valuesBySnapshot, nutrientRefs)).toList();
    }

    private static RecipeRecommendationSnapshot toSnapshot(Recipe recipe,
            List<String> requested, List<RecipeIngredient> lines,
            Map<Long, IngredientReferenceSnapshot> ingredients,
            Map<Long, MeasurementUnitReferenceSnapshot> units,
            List<RecipeTagAssignmentView> tags,
            List<RecipeMealSlotAssignmentView> slots,
            RecipeNutritionSnapshot nutrition,
            Map<Long, List<RecipeNutritionValue>> values,
            Map<Long, NutritionReferenceSnapshot> nutrients) {
        if (lines.isEmpty()) { throw inconsistent(); }
        try { Recipe.validateIngredientLines(lines); }
        catch (IllegalArgumentException exception) { throw inconsistent(); }
        List<RecipeRecommendationSnapshot.IngredientRequirement> requirements = new ArrayList<>();
        for (RecipeIngredient line : lines) {
            IngredientReferenceSnapshot ingredient = ingredients.get(line.ingredientId());
            MeasurementUnitReferenceSnapshot unit = line.unitId() == null
                    ? null : units.get(line.unitId());
            if (ingredient == null || (line.unitId() != null && unit == null)) {
                throw inconsistent();
            }
            requirements.add(new RecipeRecommendationSnapshot.IngredientRequirement(
                    ingredient.publicId(), line.quantity(), unit == null ? null : unit.code(),
                    line.isOptional(), line.allowSubstitution()));
        }
        List<String> dietary = tags.stream().filter(tag -> tag.tagKind() == RecipeTagKind.DIET)
                .map(RecipeTagAssignmentView::code).sorted().toList();
        List<String> otherTags = tags.stream().filter(tag -> tag.tagKind() != RecipeTagKind.DIET)
                .map(RecipeTagAssignmentView::code).sorted().toList();
        List<String> mealSlots = slots.isEmpty() ? List.copyOf(requested) : slots.stream()
                .map(RecipeMealSlotAssignmentView::code).sorted().toList();
        RecipeRecommendationSnapshot.Nutrition nutritionData = null;
        if (nutrition != null) {
            if (nutrition.completenessRatio() == null) { throw inconsistent(); }
            List<RecipeRecommendationSnapshot.Value> nutrientData = new ArrayList<>();
            for (RecipeNutritionValue value : values.getOrDefault(nutrition.internalId(), List.of())) {
                NutritionReferenceSnapshot nutrient = nutrients.get(value.nutrientId());
                if (nutrient == null || value.amountPerServing() == null) { throw inconsistent(); }
                nutrientData.add(new RecipeRecommendationSnapshot.Value(nutrient.code(),
                        value.amountPerServing(), nutrient.unitCode()));
            }
            nutrientData.sort(Comparator.comparing(RecipeRecommendationSnapshot.Value::nutrientCode));
            nutritionData = new RecipeRecommendationSnapshot.Nutrition(
                    nutrition.completenessRatio(), nutrientData);
        }
        return new RecipeRecommendationSnapshot(recipe.publicId(),
                BigDecimal.valueOf(recipe.servings()),
                RecipeSmallInt.toInteger(recipe.totalMinutes()), mealSlots, dietary,
                otherTags, requirements, nutritionData);
    }

    private static Map<Long, List<RecipeTagAssignmentView>> groupTags(
            Collection<RecipeTagAssignmentView> assignments) {
        Map<Long, List<RecipeTagAssignmentView>> grouped = new HashMap<>();
        for (RecipeTagAssignmentView value : assignments) {
            grouped.computeIfAbsent(value.recipeId(), ignored -> new ArrayList<>()).add(value);
        }
        return grouped;
    }

    private static Map<Long, List<RecipeMealSlotAssignmentView>> groupSlots(
            Collection<RecipeMealSlotAssignmentView> assignments) {
        Map<Long, List<RecipeMealSlotAssignmentView>> grouped = new HashMap<>();
        for (RecipeMealSlotAssignmentView value : assignments) {
            grouped.computeIfAbsent(value.recipeId(), ignored -> new ArrayList<>()).add(value);
        }
        return grouped;
    }

    private static ReferenceDataIntegrityException inconsistent() {
        return new ReferenceDataIntegrityException("Recipe recommendation data is inconsistent");
    }
}
