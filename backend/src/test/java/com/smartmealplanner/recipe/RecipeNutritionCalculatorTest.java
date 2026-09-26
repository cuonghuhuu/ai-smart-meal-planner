package com.smartmealplanner.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

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

class RecipeNutritionCalculatorTest {

    private static final RecipeNutritionCalculator CALCULATOR =
            new RecipeNutritionCalculator();

    @Test
    void calculatesPerHundredGramFoodPerServingAndKeepsMissingNutrientsUnknown() {
        RecipeNutritionCalculation result = calculate(
                recipe(2),
                List.of(line(20L, null, 50, 1L)),
                catalog(
                        Map.of(20L, ingredient(20L, 10L, null, List.of())),
                        Map.of(10L, food(
                                10L,
                                NutritionBasis.PER_100_G,
                                null,
                                3,
                                List.of(
                                        nutrient(1L, "ENERGY", "200"),
                                        nutrient(2L, "PROTEIN", "10"))))),
                Map.of(1L, unitReference(unit(1L, "g", MeasurementUnitType.MASS,
                        null, null, null))));

        assertThat(result.completenessRatio()).isEqualByComparingTo("1.0000");
        assertThat(result.ingredientRevision()).isEqualTo(3);
        assertThat(result.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("50");
        assertThat(result.amountPerServingByNutrient().get(2L))
                .isEqualByComparingTo("2.5");
        assertThat(result.amountPerServingByNutrient()).doesNotContainKey(3L);
    }

    @Test
    void preservesAnExplicitKnownZeroNutrient() {
        RecipeNutritionCalculation result = calculate(
                recipe(1),
                List.of(line(20L, null, 100, 1L)),
                catalog(
                        Map.of(20L, ingredient(20L, 10L, null, List.of())),
                        Map.of(10L, food(
                                10L,
                                NutritionBasis.PER_100_G,
                                null,
                                1,
                                List.of(nutrient(2L, "PROTEIN", "0"))))),
                Map.of(1L, unitReference(unit(1L, "g", MeasurementUnitType.MASS,
                        null, null, null))));

        assertThat(result.amountPerServingByNutrient()).containsKey(2L);
        assertThat(result.amountPerServingByNutrient().get(2L))
                .isEqualByComparingTo("0");
    }

    @Test
    void convertsKilogramsAndTablespoonsUsingUniversalUnitMetadata() {
        RecipeNutritionCalculation mass = calculate(
                recipe(1),
                List.of(line(20L, null, "0.5", 2L)),
                catalog(
                        Map.of(20L, ingredient(20L, 10L, null, List.of())),
                        Map.of(10L, food(10L, NutritionBasis.PER_100_G,
                                null, 1, List.of(nutrient(1L, "ENERGY", "100"))))),
                Map.of(2L, unitReference(unit(2L, "kg", MeasurementUnitType.MASS,
                        1L, "g", "1000"))));
        assertThat(mass.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("500");

        RecipeNutritionCalculation volume = calculate(
                recipe(1),
                List.of(line(20L, null, "2", 3L)),
                catalog(
                        Map.of(20L, ingredient(20L, 11L, null, List.of())),
                        Map.of(11L, food(11L, NutritionBasis.PER_100_ML,
                                null, 1, List.of(nutrient(1L, "ENERGY", "100"))))),
                Map.of(3L, unitReference(unit(3L, "tbsp", MeasurementUnitType.VOLUME,
                        4L, "ml", "15"))));
        assertThat(volume.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("30");
    }

    @Test
    void usesIngredientConversionPieceWeightAndFoodDensityWithoutGuessing() {
        Unit cup = unit(5L, "cup-custom", MeasurementUnitType.VOLUME,
                4L, "ml", "250");
        Unit gram = unit(1L, "g", MeasurementUnitType.MASS,
                null, null, null);
        RecipeNutritionCalculation ingredientConversion = calculate(
                recipe(1),
                List.of(line(20L, null, "1", 5L)),
                catalog(
                        Map.of(20L, ingredient(
                                20L,
                                10L,
                                null,
                                List.of(),
                                List.of(new IngredientConversion(
                                        cup, new BigDecimal("1"),
                                        gram, new BigDecimal("120"))))),
                        Map.of(10L, food(10L, NutritionBasis.PER_100_G,
                                null, 1, List.of(nutrient(1L, "ENERGY", "100"))))),
                Map.of(5L, unitReference(cup)));
        assertThat(ingredientConversion.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("120");

        RecipeNutritionCalculation piece = calculate(
                recipe(1),
                List.of(line(20L, null, "2", 6L)),
                catalog(
                        Map.of(20L, ingredient(20L, 10L,
                                new BigDecimal("50"), List.of())),
                        Map.of(10L, food(10L, NutritionBasis.PER_100_G,
                                null, 1, List.of(nutrient(1L, "ENERGY", "100"))))),
                Map.of(6L, unitReference(unit(6L, "piece", MeasurementUnitType.COUNT,
                        null, null, null))));
        assertThat(piece.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("100");

        RecipeNutritionCalculation density = calculate(
                recipe(1),
                List.of(line(20L, null, "100", 7L)),
                catalog(
                        Map.of(20L, ingredient(20L, 10L, null, List.of())),
                        Map.of(10L, food(10L, NutritionBasis.PER_100_G,
                                new BigDecimal("0.8"), 1,
                                List.of(nutrient(1L, "ENERGY", "100"))))),
                Map.of(7L, unitReference(unit(7L, "ml", MeasurementUnitType.VOLUME,
                        null, null, null))));
        assertThat(density.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("80");

        RecipeNutritionCalculation inverseDensity = calculate(
                recipe(1),
                List.of(line(20L, null, "80", 1L)),
                catalog(
                        Map.of(20L, ingredient(20L, 12L, null, List.of())),
                        Map.of(12L, food(12L, NutritionBasis.PER_100_ML,
                                new BigDecimal("0.8"), 1,
                                List.of(nutrient(1L, "ENERGY", "100"))))),
                Map.of(1L, unitReference(unit(1L, "g", MeasurementUnitType.MASS,
                        null, null, null))));
        assertThat(inverseDensity.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("100");
    }

    @Test
    void usesFoodServingAndYieldFactorButDoesNotApplyYieldToPinnedFood() {
        Unit slice = unit(8L, "slice", MeasurementUnitType.COUNT,
                null, null, null);
        FoodServingFact serving = new FoodServingFact(
                "one slice", new BigDecimal("1"), slice,
                new BigDecimal("30"), null, true);
        RecipeNutritionCatalogSnapshot catalog = catalog(
                Map.of(20L, ingredient(
                        20L,
                        10L,
                        null,
                        List.of(new FoodMapping(10L, new BigDecimal("0.8"), true)))),
                Map.of(10L, food(10L, NutritionBasis.PER_100_G,
                        null, 5, List.of(nutrient(1L, "ENERGY", "100")),
                        List.of(serving))));

        RecipeNutritionCalculation defaultFood = calculate(
                recipe(1),
                List.of(line(20L, null, "2", 8L)),
                catalog,
                Map.of(8L, unitReference(slice)));
        assertThat(defaultFood.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("48");

        RecipeNutritionCalculation pinnedFood = calculate(
                recipe(1),
                List.of(line(20L, 10L, "2", 8L)),
                catalog,
                Map.of(8L, unitReference(slice)));
        assertThat(pinnedFood.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("60");
    }

    @Test
    void reportsPartialAndUnknownLinesWithoutCreatingZeros() {
        RecipeNutritionCalculation result = calculate(
                recipe(1),
                List.of(
                        line(20L, null, "100", 1L),
                        line(21L, null, "100", 1L),
                        line(22L, null, null, null)),
                catalog(
                        Map.of(
                                20L, ingredient(20L, 10L, null, List.of()),
                                21L, ingredient(21L, 11L, null, List.of()),
                                22L, ingredient(22L, null, null, List.of())),
                        Map.of(
                                10L, food(10L, NutritionBasis.PER_100_G,
                                        null, 1, List.of(nutrient(1L, "ENERGY", "100"))),
                                11L, food(11L, NutritionBasis.PER_100_G,
                                        null, 2, List.of(nutrient(1L, "ENERGY", "50"))))),
                Map.of(1L, unitReference(unit(1L, "g", MeasurementUnitType.MASS,
                        null, null, null))));

        assertThat(result.resolvedLineCount()).isEqualTo(2);
        assertThat(result.completenessRatio()).isEqualByComparingTo("0.6667");
        assertThat(result.amountPerServingByNutrient().get(1L))
                .isEqualByComparingTo("150");
        assertThat(result.ingredientRevision()).isEqualTo(2);
    }

    @Test
    void zeroIngredientLinesHaveZeroCompletenessAndNoValues() {
        RecipeNutritionCalculation result = calculate(
                recipe(1),
                List.of(),
                catalog(Map.of(), Map.of()),
                Map.of());

        assertThat(result.completenessRatio()).isEqualByComparingTo("0.0000");
        assertThat(result.amountPerServingByNutrient()).isEmpty();
        assertThat(result.ingredientRevision()).isZero();
    }

    @Test
    void ambiguousServingAndUnknownConversionRemainUnresolved() {
        Unit custom = unit(9L, "custom", MeasurementUnitType.COUNT,
                null, null, null);
        RecipeNutritionCatalogSnapshot catalog = catalog(
                Map.of(20L, ingredient(20L, 10L, null, List.of())),
                Map.of(10L, food(
                        10L,
                        NutritionBasis.PER_100_G,
                        null,
                        1,
                        List.of(nutrient(1L, "ENERGY", "100")),
                        List.of(
                                new FoodServingFact("a", new BigDecimal("1"), custom,
                                        new BigDecimal("20"), null, false),
                                new FoodServingFact("b", new BigDecimal("1"), custom,
                                        new BigDecimal("30"), null, false)))));

        RecipeNutritionCalculation result = calculate(
                recipe(1),
                List.of(line(20L, null, "1", 9L)),
                catalog,
                Map.of(9L, unitReference(custom)));

        assertThat(result.resolvedLineCount()).isZero();
        assertThat(result.amountPerServingByNutrient()).isEmpty();
    }

    private static RecipeNutritionCalculation calculate(
            Recipe recipe,
            List<RecipeIngredient> lines,
            RecipeNutritionCatalogSnapshot catalog,
            Map<Long, MeasurementUnitReferenceSnapshot> units) {
        return CALCULATOR.calculate(recipe, lines, catalog, units);
    }

    private static Recipe recipe(int servings) {
        return new Recipe(
                UUID.randomUUID(),
                "Nutrition test recipe",
                "nutrition-test-" + UUID.randomUUID(),
                servings,
                null,
                null,
                RecipeDifficulty.EASY,
                null,
                null,
                null,
                RecipeSource.CURATED,
                "test",
                RecipeStatus.DRAFT,
                null,
                null);
    }

    private static RecipeIngredient line(
            Long ingredientId,
            Long foodId,
            int quantity,
            Long unitId) {
        return line(ingredientId, foodId, Integer.toString(quantity), unitId);
    }

    private static RecipeIngredient line(
            Long ingredientId,
            Long foodId,
            String quantity,
            Long unitId) {
        return new RecipeIngredient(
                1L,
                ingredientId.intValue(),
                ingredientId,
                foodId,
                quantity == null ? null : new BigDecimal(quantity),
                unitId,
                null,
                false,
                true,
                null);
    }

    private static IngredientNutrition ingredient(
            Long id,
            Long defaultFoodId,
            BigDecimal pieceWeight,
            List<FoodMapping> mappings) {
        return ingredient(id, defaultFoodId, pieceWeight, mappings, List.of());
    }

    private static IngredientNutrition ingredient(
            Long id,
            Long defaultFoodId,
            BigDecimal pieceWeight,
            List<FoodMapping> mappings,
            List<IngredientConversion> conversions) {
        return new IngredientNutrition(
                id,
                defaultFoodId,
                pieceWeight,
                mappings,
                conversions);
    }

    private static FoodNutrition food(
            Long id,
            NutritionBasis basis,
            BigDecimal density,
            int revision,
            List<NutrientFact> nutrients) {
        return food(id, basis, density, revision, nutrients, List.of());
    }

    private static FoodNutrition food(
            Long id,
            NutritionBasis basis,
            BigDecimal density,
            int revision,
            List<NutrientFact> nutrients,
            List<FoodServingFact> servings) {
        return new FoodNutrition(id, basis, density, revision, nutrients, servings);
    }

    private static NutrientFact nutrient(Long id, String code, String amount) {
        return new NutrientFact(id, code, new BigDecimal(amount));
    }

    private static RecipeNutritionCatalogSnapshot catalog(
            Map<Long, IngredientNutrition> ingredients,
            Map<Long, FoodNutrition> foods) {
        return new RecipeNutritionCatalogSnapshot(ingredients, foods);
    }

    private static Unit unit(
            Long id,
            String code,
            MeasurementUnitType type,
            Long baseId,
            String baseCode,
            String factor) {
        return new Unit(
                id,
                code,
                type,
                baseId,
                baseCode,
                factor == null ? null : new BigDecimal(factor));
    }

    private static MeasurementUnitReferenceSnapshot unitReference(Unit unit) {
        return new MeasurementUnitReferenceSnapshot(
                unit.internalId(),
                unit.code(),
                unit.code(),
                unit.unitType(),
                unit.baseUnitId(),
                unit.baseUnitCode(),
                unit.factorToBaseUnit());
    }
}
