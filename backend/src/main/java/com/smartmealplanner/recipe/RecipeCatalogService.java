package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.food.FoodReferenceQueryService;
import com.smartmealplanner.food.FoodReferenceSnapshot;
import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.nutrition.application.NutritionReferenceQueryService;
import com.smartmealplanner.nutrition.application.NutritionReferenceSnapshot;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only orchestration for the published Recipe catalog. */
@Service
public class RecipeCatalogService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeStepRepository steps;
    private final RecipeTagRepository tags;
    private final MealSlotTypeRepository mealSlots;
    private final RecipeNutritionSnapshotRepository nutritionSnapshots;
    private final RecipeNutritionValueRepository nutritionValues;
    private final IngredientReferenceQueryService ingredientReferences;
    private final FoodReferenceQueryService foodReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;
    private final NutritionReferenceQueryService nutrientReferences;

    RecipeCatalogService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeStepRepository steps,
            RecipeTagRepository tags,
            MealSlotTypeRepository mealSlots,
            RecipeNutritionSnapshotRepository nutritionSnapshots,
            RecipeNutritionValueRepository nutritionValues,
            IngredientReferenceQueryService ingredientReferences,
            FoodReferenceQueryService foodReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            NutritionReferenceQueryService nutrientReferences) {

        this.recipes = recipes;
        this.ingredients = ingredients;
        this.steps = steps;
        this.tags = tags;
        this.mealSlots = mealSlots;
        this.nutritionSnapshots = nutritionSnapshots;
        this.nutritionValues = nutritionValues;
        this.ingredientReferences = ingredientReferences;
        this.foodReferences = foodReferences;
        this.unitReferences = unitReferences;
        this.nutrientReferences = nutrientReferences;
    }

    @Transactional(readOnly = true)
    public RecipeCatalogPage getRecipes(
            String query,
            String mealSlotCode,
            String tagCode,
            Integer maxMinutes,
            int page,
            int size) {

        validatePage(page, size);
        String normalizedQuery = normalizeQuery(query);
        String normalizedMealSlot = normalizeCode(mealSlotCode);
        String normalizedTag = normalizeCode(tagCode);
        validateMaxMinutes(maxMinutes);

        if (normalizedMealSlot != null
                && !mealSlots.existsByCode(normalizedMealSlot)) {
            throw invalidRequest();
        }
        if (normalizedTag != null && !tags.existsByCode(normalizedTag)) {
            throw invalidRequest();
        }

        Page<Recipe> result = recipes.findPublished(
                normalizedQuery,
                normalizedMealSlot,
                normalizedTag,
                maxMinutes,
                PageRequest.of(page, size));

        return new RecipeCatalogPage(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                summaries(result.getContent()));
    }

    @Transactional(readOnly = true)
    public RecipeDetailView getRecipe(UUID publicId) {
        if (publicId == null) {
            throw invalidRequest();
        }

        Recipe recipe = recipes.findPublishedByPublicId(
                        RecipeIds.uuidToBytes(publicId))
                .orElseThrow(() -> new RecipeException(
                        RecipeFailure.RECIPE_NOT_FOUND));

        if (recipe.internalId() == null) {
            throw corrupted();
        }

        List<RecipeIngredient> lines = ingredients
                .findByRecipeIdOrderByLineNumberAsc(recipe.internalId());
        try {
            Recipe.validateIngredientLines(lines);
        } catch (IllegalArgumentException exception) {
            throw corrupted();
        }
        Map<Long, IngredientReferenceSnapshot> ingredientSnapshots =
                resolveIngredients(lines);
        Map<Long, FoodReferenceSnapshot> foodSnapshots = resolveFoods(lines);
        Map<Long, MeasurementUnitReferenceSnapshot> units = resolveUnits(lines);

        List<RecipeIngredientView> ingredientViews = lines.stream()
                .map(line -> ingredientView(
                        line, ingredientSnapshots, foodSnapshots, units))
                .toList();

        List<RecipeStepView> stepViews = steps
                .findByRecipeIdOrderByStepNumberAsc(recipe.internalId())
                .stream()
                .map(RecipeCatalogService::stepView)
                .toList();

        List<RecipeTagView> tagViews = tags
                .findByRecipeIdOrderByDisplayNameAscCodeAsc(recipe.internalId())
                .stream()
                .map(RecipeCatalogService::tagView)
                .toList();

        List<RecipeMealSlotView> mealSlotViews = mealSlots
                .findByRecipeIdOrderByDisplayOrderAscCodeAsc(recipe.internalId())
                .stream()
                .map(RecipeCatalogService::mealSlotView)
                .toList();

        RecipeNutritionSnapshotView nutrition = currentNutrition(
                recipe.internalId());

        return new RecipeDetailView(
                recipe.publicId(),
                recipe.title(),
                recipe.slug(),
                recipe.summary(),
                RecipeSmallInt.toInteger(recipe.servings()),
                RecipeSmallInt.toInteger(recipe.prepMinutes()),
                RecipeSmallInt.toInteger(recipe.cookMinutes()),
                RecipeSmallInt.toInteger(recipe.totalMinutes()),
                recipe.difficulty(),
                recipe.instructionsNote(),
                recipe.imageUrl(),
                recipe.source(),
                recipe.sourceReference(),
                recipe.status(),
                recipe.publishedAt(),
                ingredientViews,
                stepViews,
                tagViews,
                mealSlotViews,
                nutrition);
    }

    @Transactional(readOnly = true)
    public List<RecipeTagView> getRecipeTags() {
        return tags.findAllByOrderByDisplayNameAscCodeAsc()
                .stream()
                .map(RecipeCatalogService::tagView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecipeMealSlotView> getMealSlotTypes() {
        return mealSlots.findAllByOrderByDisplayOrderAscCodeAsc()
                .stream()
                .map(RecipeCatalogService::mealSlotView)
                .toList();
    }

    private RecipeNutritionSnapshotView currentNutrition(Long recipeId) {
        return nutritionSnapshots.findCurrentByRecipeId(recipeId)
                .map(snapshot -> nutritionView(snapshot, recipeId))
                .orElse(null);
    }

    private RecipeNutritionSnapshotView nutritionView(
            RecipeNutritionSnapshot snapshot,
            Long recipeId) {

        if (snapshot.internalId() == null
                || snapshot.recipeId() == null
                || !recipeId.equals(snapshot.recipeId())
                || snapshot.computedAt() == null
                || snapshot.ingredientRevision() == null
                || snapshot.ingredientRevision() < 0
                || snapshot.completenessRatio() == null
                || snapshot.completenessRatio().signum() < 0
                || snapshot.completenessRatio().compareTo(BigDecimal.ONE) > 0) {

            throw corrupted();
        }

        List<RecipeNutritionValue> values = nutritionValues
                .findBySnapshotIdOrderByNutrient(snapshot.internalId());
        Set<Long> nutrientIds = values.stream()
                .map(RecipeNutritionValue::nutrientId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, NutritionReferenceSnapshot> references = safeNutrients(nutrientIds);

        List<RecipeNutritionValueView> valueViews = values.stream()
                .map(value -> {
                    NutritionReferenceSnapshot reference = references.get(
                            value.nutrientId());
                    if (reference == null
                            || value.amountPerServing() == null
                            || value.amountPerServing().signum() < 0) {
                        throw corrupted();
                    }
                    return new RecipeNutritionValueView(
                            reference.code(),
                            reference.displayName(),
                            value.amountPerServing(),
                            reference.unitCode(),
                            reference.unitDisplayName());
                })
                .toList();

        return new RecipeNutritionSnapshotView(
                snapshot.computedAt(),
                snapshot.ingredientRevision(),
                snapshot.completenessRatio(),
                snapshot.computationNote(),
                valueViews);
    }

    private RecipeIngredientView ingredientView(
            RecipeIngredient line,
            Map<Long, IngredientReferenceSnapshot> ingredientSnapshots,
            Map<Long, FoodReferenceSnapshot> foodSnapshots,
            Map<Long, MeasurementUnitReferenceSnapshot> units) {

        if (line.lineNumber() == null || line.lineNumber() < 1
                || line.ingredientId() == null) {
            throw corrupted();
        }

        IngredientReferenceSnapshot ingredient = requireReference(
                ingredientSnapshots, line.ingredientId());
        FoodReferenceSnapshot food = line.foodId() == null
                ? null
                : requireReference(foodSnapshots, line.foodId());

        if ((line.quantity() == null) != (line.unitId() == null)
                || (line.quantity() != null && line.quantity().signum() <= 0)
                || (line.preparationNote() != null
                && line.preparationNote().length() > 200)
                || (line.sectionLabel() != null
                && line.sectionLabel().length() > 80)) {
            throw corrupted();
        }
        MeasurementUnitReferenceSnapshot unit = line.unitId() == null
                ? null
                : requireReference(units, line.unitId());

        return new RecipeIngredientView(
                RecipeSmallInt.toInteger(line.lineNumber()),
                ingredient.publicId(),
                ingredient.code(),
                ingredient.displayName(),
                food == null ? null : food.publicId(),
                food == null ? null : food.code(),
                food == null ? null : food.displayName(),
                line.quantity(),
                unit == null ? null : unit.code(),
                unit == null ? null : unit.displayName(),
                line.preparationNote(),
                line.isOptional(),
                line.allowSubstitution(),
                line.sectionLabel());
    }

    private Map<Long, IngredientReferenceSnapshot> resolveIngredients(
            List<RecipeIngredient> lines) {
        Set<Long> ids = lines.stream()
                .map(RecipeIngredient::ingredientId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return safeIngredients(ids);
    }

    private Map<Long, FoodReferenceSnapshot> resolveFoods(
            List<RecipeIngredient> lines) {
        Set<Long> ids = lines.stream()
                .map(RecipeIngredient::foodId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return safeFoods(ids);
    }

    private Map<Long, MeasurementUnitReferenceSnapshot> resolveUnits(
            List<RecipeIngredient> lines) {
        Set<Long> ids = lines.stream()
                .map(RecipeIngredient::unitId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return safeUnits(ids);
    }

    private Map<Long, IngredientReferenceSnapshot> safeIngredients(
            Collection<Long> ids) {
        try {
            return ingredientReferences.resolveByInternalIds(ids);
        } catch (ReferenceDataIntegrityException exception) {
            throw corrupted();
        }
    }

    private Map<Long, FoodReferenceSnapshot> safeFoods(Collection<Long> ids) {
        try {
            return foodReferences.resolveByInternalIds(ids);
        } catch (ReferenceDataIntegrityException exception) {
            throw corrupted();
        }
    }

    private Map<Long, MeasurementUnitReferenceSnapshot> safeUnits(
            Collection<Long> ids) {
        try {
            return unitReferences.resolveByInternalIds(ids);
        } catch (ReferenceDataIntegrityException exception) {
            throw corrupted();
        }
    }

    private Map<Long, NutritionReferenceSnapshot> safeNutrients(
            Collection<Long> ids) {
        try {
            return nutrientReferences.resolveByInternalIds(ids);
        } catch (ReferenceDataIntegrityException exception) {
            throw corrupted();
        }
    }

    private static <T> T requireReference(Map<Long, T> values, Long id) {
        T value = values.get(id);
        if (value == null) {
            throw corrupted();
        }
        return value;
    }

    private List<RecipeSummaryView> summaries(List<Recipe> pageRecipes) {
        Set<Long> recipeIds = new LinkedHashSet<>();
        for (Recipe recipe : pageRecipes) {
            if (recipe == null || recipe.internalId() == null) {
                throw corrupted();
            }
            recipeIds.add(recipe.internalId());
        }

        Map<Long, List<RecipeTagView>> tagsByRecipe = batchTags(recipeIds);
        Map<Long, List<RecipeMealSlotView>> mealSlotsByRecipe =
                batchMealSlots(recipeIds);

        return pageRecipes.stream()
                .map(recipe -> summary(
                        recipe,
                        tagsByRecipe.getOrDefault(recipe.internalId(), List.of()),
                        mealSlotsByRecipe.getOrDefault(
                                recipe.internalId(), List.of())))
                .toList();
    }

    private Map<Long, List<RecipeTagView>> batchTags(Set<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<RecipeTagView>> grouped = new LinkedHashMap<>();
        for (RecipeTagAssignmentView assignment : tags
                .findByRecipeIdsOrderByDisplayNameAscCodeAsc(recipeIds)) {

            if (assignment == null
                    || assignment.recipeId() == null
                    || !recipeIds.contains(assignment.recipeId())) {
                throw corrupted();
            }

            grouped.computeIfAbsent(assignment.recipeId(), key -> new ArrayList<>())
                    .add(tagView(assignment));
        }
        return grouped;
    }

    private Map<Long, List<RecipeMealSlotView>> batchMealSlots(
            Set<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<RecipeMealSlotView>> grouped = new LinkedHashMap<>();
        for (RecipeMealSlotAssignmentView assignment : mealSlots
                .findByRecipeIdsOrderByDisplayOrderAscCodeAsc(recipeIds)) {

            if (assignment == null
                    || assignment.recipeId() == null
                    || !recipeIds.contains(assignment.recipeId())) {
                throw corrupted();
            }

            grouped.computeIfAbsent(assignment.recipeId(), key -> new ArrayList<>())
                    .add(mealSlotView(assignment));
        }
        return grouped;
    }

    private static RecipeSummaryView summary(
            Recipe recipe,
            List<RecipeTagView> tags,
            List<RecipeMealSlotView> mealSlots) {

        if (recipe.publicId() == null || recipe.title() == null
                || recipe.difficulty() == null || recipe.source() == null) {
            throw corrupted();
        }
        return new RecipeSummaryView(
                recipe.publicId(),
                recipe.title(),
                recipe.summary(),
                RecipeSmallInt.toInteger(recipe.servings()),
                RecipeSmallInt.toInteger(recipe.prepMinutes()),
                RecipeSmallInt.toInteger(recipe.cookMinutes()),
                RecipeSmallInt.toInteger(recipe.totalMinutes()),
                recipe.difficulty(),
                recipe.imageUrl(),
                recipe.source(),
                List.copyOf(tags),
                List.copyOf(mealSlots));
    }

    private static RecipeStepView stepView(RecipeStep step) {
        if (step.stepNumber() == null || step.stepNumber() < 1
                || step.instruction() == null || step.instruction().isBlank()
                || step.instruction().length() > 2000
                || (step.durationMinutes() != null
                && (step.durationMinutes() < 0
                || step.durationMinutes() > 10080))) {
            throw corrupted();
        }
        return new RecipeStepView(
                RecipeSmallInt.toInteger(step.stepNumber()),
                step.instruction(),
                RecipeSmallInt.toInteger(step.durationMinutes()));
    }

    private static RecipeTagView tagView(RecipeTag tag) {
        if (tag.code() == null || tag.displayName() == null
                || tag.tagKind() == null) {
            throw corrupted();
        }
        return new RecipeTagView(tag.code(), tag.displayName(), tag.tagKind());
    }

    private static RecipeTagView tagView(RecipeTagAssignmentView tag) {
        if (tag.code() == null || tag.displayName() == null
                || tag.tagKind() == null) {
            throw corrupted();
        }
        return new RecipeTagView(tag.code(), tag.displayName(), tag.tagKind());
    }

    private static RecipeMealSlotView mealSlotView(MealSlotType slot) {
        if (slot.code() == null || slot.displayName() == null
                || slot.displayOrder() == null) {
            throw corrupted();
        }
        return new RecipeMealSlotView(
                slot.code(),
                slot.displayName(),
                slot.displayOrder(),
                slot.typicalTime(),
                slot.isMainMeal());
    }

    private static RecipeMealSlotView mealSlotView(
            RecipeMealSlotAssignmentView slot) {
        if (slot.code() == null || slot.displayName() == null
                || slot.displayOrder() == null) {
            throw corrupted();
        }
        return new RecipeMealSlotView(
                slot.code(),
                slot.displayName(),
                slot.displayOrder(),
                slot.typicalTime(),
                slot.mainMeal());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidRequest();
        }
    }

    private static String normalizeQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void validateMaxMinutes(Integer maxMinutes) {
        if (maxMinutes != null && (maxMinutes < 0 || maxMinutes > 10080)) {
            throw invalidRequest();
        }
    }

    private static RecipeException invalidRequest() {
        return new RecipeException(RecipeFailure.INVALID_REQUEST);
    }

    private static RecipeException corrupted() {
        return new RecipeException(RecipeFailure.CORRUPTED_RECIPE_DATA);
    }
}
