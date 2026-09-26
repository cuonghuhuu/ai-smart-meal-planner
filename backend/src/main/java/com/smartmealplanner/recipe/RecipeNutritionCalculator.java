package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.smartmealplanner.food.NutritionBasis;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.FoodMapping;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.FoodNutrition;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.FoodServingFact;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.IngredientConversion;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.IngredientNutrition;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.NutrientFact;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.Unit;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitType;

/** Pure BigDecimal recipe-nutrition calculation over batch-resolved facts. */
final class RecipeNutritionCalculator {

    private static final MathContext MATH = MathContext.DECIMAL128;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    RecipeNutritionCalculation calculate(
            Recipe recipe,
            List<RecipeIngredient> lines,
            RecipeNutritionCatalogSnapshot catalog,
            Map<Long, MeasurementUnitReferenceSnapshot> units) {

        if (recipe == null || lines == null || catalog == null || units == null
                || recipe.servings() == null || recipe.servings() < 1) {
            throw corrupted();
        }

        try {
            Recipe.validateIngredientLines(lines);
        } catch (IllegalArgumentException exception) {
            throw corrupted();
        }

        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        int resolvedLines = 0;
        int maximumFoodRevision = 0;

        for (RecipeIngredient line : lines) {
            IngredientSelection selection = selectFood(line, catalog);
            if (selection == null) {
                continue;
            }

            FoodNutrition food = selection.food();
            if (food == null
                    || food.nutritionBasis() == null
                    || food.revision() == null
                    || food.revision() < 0
                    || (food.densityGPerMl() != null
                    && food.densityGPerMl().signum() <= 0)) {
                throw corrupted();
            }
            maximumFoodRevision = Math.max(maximumFoodRevision, food.revision());
            if (line.quantity() == null || food.nutrientFacts().isEmpty()) {
                continue;
            }

            MeasurementUnitReferenceSnapshot sourceUnit = units.get(line.unitId());
            if (sourceUnit == null) {
                throw corrupted();
            }

            MeasuredQuantity measured = resolveQuantity(
                    line.quantity(),
                    sourceUnit,
                    selection.ingredient(),
                    food);
            if (measured == null) {
                continue;
            }

            measured = applyYield(
                    measured,
                    selection.yieldFactor(),
                    food);
            if (measured == null) {
                continue;
            }

            BigDecimal basisQuantity = food.nutritionBasis() == NutritionBasis.PER_100_G
                    ? measured.grams()
                    : measured.milliliters();
            if (basisQuantity == null || basisQuantity.signum() <= 0) {
                continue;
            }

            resolvedLines++;
            for (NutrientFact fact : food.nutrientFacts()) {
                if (fact.nutrientId() == null
                        || fact.amount() == null
                        || fact.amount().signum() < 0) {
                    throw corrupted();
                }
                BigDecimal contribution = fact.amount()
                        .multiply(basisQuantity, MATH)
                        .divide(HUNDRED, MATH);
                totals.merge(fact.nutrientId(), contribution,
                        (left, right) -> left.add(right, MATH));
            }
        }

        BigDecimal completeness = lines.isEmpty()
                ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(resolvedLines)
                        .divide(BigDecimal.valueOf(lines.size()), MATH)
                        .setScale(4, RoundingMode.HALF_UP);
        BigDecimal servings = BigDecimal.valueOf(recipe.servings().longValue());
        Map<Long, BigDecimal> perServing = new LinkedHashMap<>();
        totals.forEach((nutrientId, total) -> perServing.put(
                nutrientId,
                total.divide(servings, MATH)));

        String note = "Resolved " + resolvedLines + "/" + lines.size()
                + " ingredient lines; unresolved lines were excluded; missing nutrient facts were not treated as zero.";
        return new RecipeNutritionCalculation(
                perServing,
                completeness,
                resolvedLines,
                lines.size(),
                maximumFoodRevision,
                note);
    }

    private static IngredientSelection selectFood(
            RecipeIngredient line,
            RecipeNutritionCatalogSnapshot catalog) {

        IngredientNutrition ingredient = catalog.ingredients().get(line.ingredientId());
        if (ingredient == null) {
            throw corrupted();
        }

        if (line.foodId() != null) {
            FoodNutrition pinned = catalog.foods().get(line.foodId());
            if (pinned == null) {
                throw corrupted();
            }
            if (!ingredient.foodMappings().isEmpty()
                    && ingredient.foodMappings().stream()
                            .noneMatch(mapping -> line.foodId().equals(mapping.foodId()))) {
                throw corrupted();
            }
            return new IngredientSelection(ingredient, pinned, ONE);
        }

        List<FoodMapping> primaryMappings = ingredient.foodMappings().stream()
                .filter(FoodMapping::primary)
                .toList();
        if (primaryMappings.size() > 1) {
            throw corrupted();
        }
        Long primaryFoodId = primaryMappings.isEmpty()
                ? null
                : primaryMappings.getFirst().foodId();
        if (ingredient.defaultFoodId() != null
                && primaryFoodId != null
                && !ingredient.defaultFoodId().equals(primaryFoodId)) {
            throw corrupted();
        }

        Long selectedFoodId = ingredient.defaultFoodId() != null
                ? ingredient.defaultFoodId()
                : primaryFoodId;
        if (selectedFoodId == null) {
            return null;
        }

        FoodNutrition selectedFood = catalog.foods().get(selectedFoodId);
        if (selectedFood == null) {
            throw corrupted();
        }
        BigDecimal yield = ingredient.foodMappings().stream()
                .filter(mapping -> selectedFoodId.equals(mapping.foodId()))
                .map(FoodMapping::yieldFactor)
                .findFirst()
                .orElse(ONE);
        return new IngredientSelection(ingredient, selectedFood, yield);
    }

    private static MeasuredQuantity resolveQuantity(
            BigDecimal quantity,
            MeasurementUnitReferenceSnapshot sourceUnit,
            IngredientNutrition ingredient,
            FoodNutrition food) {

        UnitInfo source = UnitInfo.from(sourceUnit);
        MeasuredQuantity universal = switch (source.type()) {
            case MASS -> new MeasuredQuantity(
                    normalizeToBase(quantity, source, MeasurementUnitType.MASS, "g"),
                    null);
            case VOLUME -> new MeasuredQuantity(
                    null,
                    normalizeToBase(quantity, source, MeasurementUnitType.VOLUME, "ml"));
            default -> null;
        };

        universal = withDensity(universal, food.densityGPerMl());

        NutritionBasis basis = food.nutritionBasis();
        boolean needsMass = basis == NutritionBasis.PER_100_G;
        if (universal != null && universal.hasTarget(needsMass)) {
            return universal;
        }

        List<MeasuredQuantity> directConversions = new ArrayList<>();
        for (IngredientConversion conversion : ingredient.conversions()) {
            if (!source.id().equals(conversion.unitFrom().internalId())) {
                continue;
            }
            if (conversion.fromQuantity() == null
                    || conversion.toQuantity() == null
                    || conversion.fromQuantity().signum() <= 0
                    || conversion.toQuantity().signum() <= 0) {
                throw corrupted();
            }
            BigDecimal converted = quantity
                    .multiply(conversion.toQuantity(), MATH)
                    .divide(conversion.fromQuantity(), MATH);
            MeasuredQuantity measurable = measurable(
                    conversion.unitTo(),
                    converted);
            measurable = withDensity(measurable, food.densityGPerMl());
            if (measurable != null && measurable.hasTarget(needsMass)) {
                directConversions.add(measurable);
            }
        }
        MeasuredQuantity direct = selectDistinct(directConversions, needsMass);
        if (direct != null) {
            return direct;
        }

        MeasuredQuantity piece = null;
        if (source.type() == MeasurementUnitType.COUNT
                && "piece".equals(source.code())
                && ingredient.pieceGramWeight() != null
                && ingredient.pieceGramWeight().signum() > 0) {
            piece = new MeasuredQuantity(
                    quantity.multiply(ingredient.pieceGramWeight(), MATH),
                    null);
            if (piece.hasTarget(needsMass)) {
                return piece;
            }
        }

        MeasuredQuantity density = withDensity(
                universal == null ? piece : universal,
                food.densityGPerMl());
        if (density != null && density.hasTarget(needsMass)) {
            return density;
        }

        List<MeasuredQuantity> servingCandidates = new ArrayList<>();
        for (FoodServingFact serving : food.servings()) {
            if (serving.unit() == null
                    || !source.id().equals(serving.unit().internalId())) {
                continue;
            }
            if (serving.quantity() == null || serving.quantity().signum() <= 0) {
                throw corrupted();
            }
            BigDecimal multiplier = quantity.divide(serving.quantity(), MATH);
            BigDecimal grams = serving.gramWeight() == null
                    ? null
                    : serving.gramWeight().multiply(multiplier, MATH);
            BigDecimal milliliters = serving.milliliters() == null
                    ? null
                    : serving.milliliters().multiply(multiplier, MATH);
            if (grams == null && milliliters == null) {
                throw corrupted();
            }
            servingCandidates.add(withDensity(
                    new MeasuredQuantity(grams, milliliters),
                    food.densityGPerMl()));
        }
        return selectDistinct(servingCandidates, needsMass);
    }

    private static MeasuredQuantity measurable(Unit unit, BigDecimal quantity) {
        UnitInfo target = UnitInfo.from(unit);
        return switch (target.type()) {
            case MASS -> new MeasuredQuantity(
                    normalizeToBase(quantity, target, MeasurementUnitType.MASS, "g"),
                    null);
            case VOLUME -> new MeasuredQuantity(
                    null,
                    normalizeToBase(quantity, target, MeasurementUnitType.VOLUME, "ml"));
            default -> null;
        };
    }

    private static MeasuredQuantity selectDistinct(
            List<MeasuredQuantity> candidates,
            boolean needsMass) {
        MeasuredQuantity selected = null;
        for (MeasuredQuantity candidate : candidates) {
            if (candidate == null || !candidate.hasTarget(needsMass)) {
                continue;
            }
            if (selected == null) {
                selected = candidate;
                continue;
            }
            BigDecimal selectedAmount = selected.targetAmount(needsMass);
            BigDecimal candidateAmount = candidate.targetAmount(needsMass);
            if (selectedAmount.compareTo(candidateAmount) != 0) {
                return null;
            }
        }
        return selected;
    }

    private static MeasuredQuantity withDensity(
            MeasuredQuantity value,
            BigDecimal density) {
        if (value == null) {
            return null;
        }
        if (density == null || density.signum() <= 0) {
            return value;
        }
        BigDecimal grams = value.grams();
        BigDecimal milliliters = value.milliliters();
        if (grams != null && milliliters == null) {
            milliliters = grams.divide(density, MATH);
        } else if (milliliters != null && grams == null) {
            grams = milliliters.multiply(density, MATH);
        }
        return new MeasuredQuantity(grams, milliliters);
    }

    private static MeasuredQuantity applyYield(
            MeasuredQuantity value,
            BigDecimal yieldFactor,
            FoodNutrition food) {

        if (yieldFactor == null || yieldFactor.signum() <= 0) {
            throw corrupted();
        }
        if (yieldFactor.compareTo(ONE) == 0) {
            return value;
        }
        if (value.grams() == null) {
            return null;
        }
        BigDecimal grams = value.grams().multiply(yieldFactor, MATH);
        BigDecimal milliliters = food.densityGPerMl() == null
                ? null
                : grams.divide(food.densityGPerMl(), MATH);
        return new MeasuredQuantity(grams, milliliters);
    }

    private static BigDecimal normalizeToBase(
            BigDecimal quantity,
            UnitInfo unit,
            MeasurementUnitType expectedType,
            String expectedBaseCode) {

        if (quantity == null || quantity.signum() <= 0
                || unit.type() != expectedType) {
            return null;
        }
        if (unit.baseUnitId() == null) {
            return expectedBaseCode.equals(unit.code()) && unit.factorToBaseUnit() == null
                    ? quantity
                    : null;
        }
        if (!expectedBaseCode.equals(unit.baseUnitCode())
                || unit.factorToBaseUnit() == null
                || unit.factorToBaseUnit().signum() <= 0) {
            return null;
        }
        return quantity.multiply(unit.factorToBaseUnit(), MATH);
    }

    private static RecipeException corrupted() {
        return new RecipeException(RecipeFailure.CORRUPTED_RECIPE_DATA);
    }

    private record IngredientSelection(
            IngredientNutrition ingredient,
            FoodNutrition food,
            BigDecimal yieldFactor) {
    }

    private record MeasuredQuantity(
            BigDecimal grams,
            BigDecimal milliliters) {

        boolean hasTarget(boolean needsMass) {
            return targetAmount(needsMass) != null
                    && targetAmount(needsMass).signum() > 0;
        }

        BigDecimal targetAmount(boolean needsMass) {
            return needsMass ? grams : milliliters;
        }
    }

    private record UnitInfo(
            Long id,
            String code,
            MeasurementUnitType type,
            Long baseUnitId,
            String baseUnitCode,
            BigDecimal factorToBaseUnit) {

        static UnitInfo from(MeasurementUnitReferenceSnapshot value) {
            if (value == null || value.internalId() == null || value.code() == null
                    || value.unitType() == null) {
                throw corrupted();
            }
            return new UnitInfo(
                    value.internalId(),
                    value.code(),
                    value.unitType(),
                    value.baseUnitId(),
                    value.baseUnitCode(),
                    value.factorToBaseUnit());
        }

        static UnitInfo from(Unit value) {
            if (value == null || value.internalId() == null || value.code() == null
                    || value.unitType() == null) {
                throw corrupted();
            }
            return new UnitInfo(
                    value.internalId(),
                    value.code(),
                    value.unitType(),
                    value.baseUnitId(),
                    value.baseUnitCode(),
                    value.factorToBaseUnit());
        }
    }
}
