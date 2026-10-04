package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipe-owned read boundary for persisted-plan projections.
 *
 * <p>Unlike catalog and recommendation reads, this intentionally includes
 * archived recipes because historical meal-plan entries retain their recipe
 * foreign keys.</p>
 */
@Service
public class RecipeRequirementQueryService {

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final IngredientReferenceQueryService ingredientReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;

    public RecipeRequirementQueryService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            IngredientReferenceQueryService ingredientReferences,
            MeasurementUnitReferenceQueryService unitReferences) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.ingredientReferences = ingredientReferences;
        this.unitReferences = unitReferences;
    }

    @Transactional(readOnly = true)
    public Map<UUID, RecipeRequirementSnapshot> requirementsFor(
            Collection<UUID> recipePublicIds) {

        if (recipePublicIds == null || recipePublicIds.isEmpty()) {
            return Map.of();
        }

        Set<UUID> requested = new LinkedHashSet<>();
        for (UUID publicId : recipePublicIds) {
            if (publicId == null) {
                throw new IllegalArgumentException("recipePublicIds cannot contain null");
            }
            requested.add(publicId);
        }

        List<Recipe> selected = recipes.findAllByPublicIdIn(
                requested.stream().map(RecipeIds::uuidToBytes).toList());
        if (selected.size() != requested.size()) {
            throw inconsistent();
        }

        Set<Long> recipeIds = new HashSet<>();
        Map<Long, Recipe> recipesById = new HashMap<>();
        for (Recipe recipe : selected) {
            if (recipe == null || recipe.internalId() == null
                    || recipe.publicId() == null || recipe.servings() == null
                    || recipe.servings() <= 0
                    || !requested.contains(recipe.publicId())
                    || !recipeIds.add(recipe.internalId())) {
                throw inconsistent();
            }
            recipesById.put(recipe.internalId(), recipe);
        }

        List<RecipeIngredient> lines = ingredients.findByRecipeIds(recipeIds);
        Map<Long, List<RecipeIngredient>> linesByRecipe = new HashMap<>();
        Set<Long> ingredientIds = new HashSet<>();
        Set<Long> unitIds = new HashSet<>();
        for (RecipeIngredient line : lines) {
            if (line == null || line.recipeId() == null
                    || !recipeIds.contains(line.recipeId())
                    || line.ingredientId() == null) {
                throw inconsistent();
            }
            linesByRecipe.computeIfAbsent(line.recipeId(), ignored -> new java.util.ArrayList<>())
                    .add(line);
            ingredientIds.add(line.ingredientId());
            if (line.unitId() != null) {
                unitIds.add(line.unitId());
            }
        }

        Map<Long, IngredientReferenceSnapshot> ingredientRefs =
                ingredientReferences.resolveByInternalIds(ingredientIds);
        Map<Long, MeasurementUnitReferenceSnapshot> unitRefs =
                unitReferences.resolveByInternalIds(unitIds);
        if (ingredientRefs.size() != ingredientIds.size()
                || unitRefs.size() != unitIds.size()) {
            throw inconsistent();
        }

        Map<UUID, RecipeRequirementSnapshot> result = new LinkedHashMap<>();
        selected.stream()
                .sorted(Comparator.comparing(recipe -> recipe.publicId().toString()))
                .forEach(recipe -> {
                    List<RecipeIngredient> recipeLines = linesByRecipe.getOrDefault(
                            recipe.internalId(), List.of());
                    if (recipeLines.isEmpty()) {
                        throw inconsistent();
                    }
                    try {
                        Recipe.validateIngredientLines(recipeLines);
                    } catch (IllegalArgumentException exception) {
                        throw inconsistent();
                    }
                    recipeLines = recipeLines.stream()
                            .sorted(Comparator.comparing(RecipeIngredient::lineNumber))
                            .toList();

                    List<RecipeRequirementSnapshot.IngredientRequirement> requirements =
                            recipeLines.stream().map(line -> {
                                IngredientReferenceSnapshot ingredient =
                                        ingredientRefs.get(line.ingredientId());
                                MeasurementUnitReferenceSnapshot unit =
                                        line.unitId() == null
                                                ? null
                                                : unitRefs.get(line.unitId());
                                if (ingredient == null
                                        || (line.unitId() != null && unit == null)
                                        || (line.quantity() == null) != (unit == null)
                                        || (line.quantity() != null
                                        && line.quantity().signum() <= 0)) {
                                    throw inconsistent();
                                }
                                return new RecipeRequirementSnapshot.IngredientRequirement(
                                        ingredient.publicId(),
                                        ingredient.code(),
                                        ingredient.displayName(),
                                        line.quantity(),
                                        unit == null ? null : unit.code(),
                                        line.isOptional());
                            }).toList();

                    result.put(recipe.publicId(), new RecipeRequirementSnapshot(
                            recipe.publicId(),
                            BigDecimal.valueOf(recipe.servings()),
                            requirements));
                });

        if (result.size() != requested.size()) {
            throw inconsistent();
        }
        return Map.copyOf(result);
    }

    private static ReferenceDataIntegrityException inconsistent() {
        return new ReferenceDataIntegrityException(
                "Recipe requirement data is inconsistent");
    }
}
