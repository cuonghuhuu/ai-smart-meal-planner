package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.smartmealplanner.nutrition.persistence.MeasurementUnitType;

/**
 * Immutable, batch-resolved nutrition facts supplied to the Recipe module.
 * The records intentionally contain only computation facts and internal
 * persistence handles; they do not expose Food or Ingredient entities.
 */
public record RecipeNutritionCatalogSnapshot(
        Map<Long, IngredientNutrition> ingredients,
        Map<Long, FoodNutrition> foods) {

    public RecipeNutritionCatalogSnapshot {
        ingredients = Map.copyOf(Objects.requireNonNull(ingredients));
        foods = Map.copyOf(Objects.requireNonNull(foods));
    }

    public record IngredientNutrition(
            Long internalId,
            Long defaultFoodId,
            BigDecimal pieceGramWeight,
            List<FoodMapping> foodMappings,
            List<IngredientConversion> conversions) {

        public IngredientNutrition {
            foodMappings = List.copyOf(Objects.requireNonNull(foodMappings));
            conversions = List.copyOf(Objects.requireNonNull(conversions));
        }
    }

    public record FoodMapping(
            Long foodId,
            BigDecimal yieldFactor,
            boolean primary) {
    }

    public record IngredientConversion(
            Unit unitFrom,
            BigDecimal fromQuantity,
            Unit unitTo,
            BigDecimal toQuantity) {
    }

    public record FoodNutrition(
            Long internalId,
            NutritionBasis nutritionBasis,
            BigDecimal densityGPerMl,
            Integer revision,
            List<NutrientFact> nutrientFacts,
            List<FoodServingFact> servings) {

        public FoodNutrition {
            nutrientFacts = List.copyOf(Objects.requireNonNull(nutrientFacts));
            servings = List.copyOf(Objects.requireNonNull(servings));
        }
    }

    public record NutrientFact(
            Long nutrientId,
            String code,
            BigDecimal amount) {
    }

    public record FoodServingFact(
            String displayName,
            BigDecimal quantity,
            Unit unit,
            BigDecimal gramWeight,
            BigDecimal milliliters,
            boolean defaultServing) {
    }

    public record Unit(
            Long internalId,
            String code,
            MeasurementUnitType unitType,
            Long baseUnitId,
            String baseUnitCode,
            BigDecimal factorToBaseUnit) {
    }
}
