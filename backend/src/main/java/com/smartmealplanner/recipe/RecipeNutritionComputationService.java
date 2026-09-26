package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.food.FoodNutritionQueryService;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit, transactional Recipe nutrition recomputation capability.
 *
 * <p>There is intentionally no HTTP adapter for this service in P9B. Import
 * and curation workflows may call it later, while ordinary catalog GETs remain
 * read-only.</p>
 */
@Service
public class RecipeNutritionComputationService {

    private static final int DECIMAL_AMOUNT_PRECISION = 12;
    private static final int DECIMAL_AMOUNT_SCALE = 4;
    private static final int SNAPSHOT_RATIO_SCALE = 4;

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeNutritionSnapshotRepository snapshots;
    private final RecipeNutritionValueRepository values;
    private final FoodNutritionQueryService foodNutrition;
    private final MeasurementUnitReferenceQueryService units;
    private final RecipeNutritionCalculator calculator;
    private final Clock clock;

    @Autowired
    public RecipeNutritionComputationService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeNutritionSnapshotRepository snapshots,
            RecipeNutritionValueRepository values,
            FoodNutritionQueryService foodNutrition,
            MeasurementUnitReferenceQueryService units) {
        this(
                recipes,
                ingredients,
                snapshots,
                values,
                foodNutrition,
                units,
                new RecipeNutritionCalculator(),
                Clock.systemUTC());
    }

    RecipeNutritionComputationService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeNutritionSnapshotRepository snapshots,
            RecipeNutritionValueRepository values,
            FoodNutritionQueryService foodNutrition,
            MeasurementUnitReferenceQueryService units,
            RecipeNutritionCalculator calculator,
            Clock clock) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.snapshots = snapshots;
        this.values = values;
        this.foodNutrition = foodNutrition;
        this.units = units;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional
    public RecipeNutritionComputationResult recompute(UUID recipePublicId) {
        if (recipePublicId == null) {
            throw new RecipeException(RecipeFailure.INVALID_REQUEST);
        }

        Recipe recipe = recipes.findByPublicIdForUpdate(
                        RecipeIds.uuidToBytes(recipePublicId))
                .orElseThrow(() -> new RecipeException(
                        RecipeFailure.RECIPE_NOT_FOUND));
        if (recipe.internalId() == null) {
            throw corrupted();
        }

        Long recipeId = recipe.internalId();
        List<RecipeIngredient> lines = ingredients
                .findByRecipeIdOrderByLineNumberAsc(recipeId);
        try {
            Recipe.validateIngredientLines(lines);
        } catch (IllegalArgumentException exception) {
            throw corrupted();
        }

        Set<Long> ingredientIds = lines.stream()
                .map(RecipeIngredient::ingredientId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Long> pinnedFoodIds = lines.stream()
                .map(RecipeIngredient::foodId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Long> unitIds = lines.stream()
                .map(RecipeIngredient::unitId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<Long, MeasurementUnitReferenceSnapshot> unitFacts = resolveUnits(unitIds);
        RecipeNutritionCatalogSnapshot catalog = resolveCatalog(
                ingredientIds,
                pinnedFoodIds);
        RecipeNutritionCalculation calculation = calculator.calculate(
                recipe,
                lines,
                catalog,
                unitFacts);

        LocalDateTime computedAt = databaseTimestamp(clock);
        RecipeNutritionSnapshot current = snapshots
                .findCurrentByRecipeId(recipeId)
                .orElse(null);
        if (current != null) {
            current.markNotCurrent();
            // The functional unique index allows only one current row. Flush
            // this demotion before inserting the replacement.
            snapshots.flush();
        }

        RecipeNutritionSnapshot replacement = new RecipeNutritionSnapshot(
                recipeId,
                computedAt,
                calculation.ingredientRevision(),
                calculation.completenessRatio()
                        .setScale(SNAPSHOT_RATIO_SCALE, RoundingMode.HALF_UP),
                calculation.computationNote(),
                true);
        final RecipeNutritionSnapshot savedReplacement =
                snapshots.saveAndFlush(replacement);
        if (savedReplacement.internalId() == null) {
            throw corrupted();
        }
        final Long snapshotId = savedReplacement.internalId();

        List<RecipeNutritionValue> nutritionValues = calculation
                .amountPerServingByNutrient()
                .entrySet()
                .stream()
                .map(entry -> new RecipeNutritionValue(
                        snapshotId,
                        entry.getKey(),
                        roundAmount(entry.getValue())))
                .toList();
        values.saveAllAndFlush(nutritionValues);

        return new RecipeNutritionComputationResult(
                recipe.publicId(),
                computedAt,
                calculation.ingredientRevision(),
                calculation.completenessRatio()
                        .setScale(SNAPSHOT_RATIO_SCALE, RoundingMode.HALF_UP),
                calculation.resolvedLineCount(),
                calculation.totalLineCount());
    }

    private Map<Long, MeasurementUnitReferenceSnapshot> resolveUnits(
            Collection<Long> ids) {
        try {
            return units.resolveByInternalIds(ids);
        } catch (RecipeException exception) {
            throw exception;
        } catch (ReferenceDataIntegrityException exception) {
            throw corrupted();
        }
    }

    private RecipeNutritionCatalogSnapshot resolveCatalog(
            Collection<Long> ingredientIds,
            Collection<Long> pinnedFoodIds) {
        try {
            return foodNutrition.resolve(ingredientIds, pinnedFoodIds);
        } catch (RecipeException exception) {
            throw exception;
        } catch (ReferenceDataIntegrityException exception) {
            throw corrupted();
        }
    }

    private static LocalDateTime databaseTimestamp(Clock clock) {
        return LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private static BigDecimal roundAmount(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw corrupted();
        }
        BigDecimal rounded = value.setScale(
                DECIMAL_AMOUNT_SCALE,
                RoundingMode.HALF_UP);
        if (rounded.precision() > DECIMAL_AMOUNT_PRECISION) {
            throw corrupted();
        }
        return rounded;
    }

    private static RecipeException corrupted() {
        return new RecipeException(RecipeFailure.CORRUPTED_RECIPE_DATA);
    }
}
