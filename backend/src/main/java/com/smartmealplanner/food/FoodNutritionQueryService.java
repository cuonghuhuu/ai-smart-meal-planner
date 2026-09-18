package com.smartmealplanner.food;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.FoodMapping;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.FoodNutrition;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.FoodServingFact;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.IngredientConversion;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.IngredientNutrition;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.NutrientFact;
import com.smartmealplanner.food.RecipeNutritionCatalogSnapshot.Unit;
import com.smartmealplanner.nutrition.persistence.MeasurementUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food-module batch boundary for Recipe nutrition computation.
 *
 * <p>The service deliberately returns immutable facts instead of entities or
 * repositories. All catalog rows needed for one recipe are loaded in bounded
 * batches so Recipe never creates a per-line repository-query loop.</p>
 */
@Service
public class FoodNutritionQueryService {

    private final FoodRepository foods;
    private final FoodNutrientRepository foodNutrients;
    private final FoodServingRepository foodServings;
    private final IngredientRepository ingredients;
    private final IngredientFoodRepository ingredientFoods;
    private final IngredientUnitConversionRepository conversions;

    public FoodNutritionQueryService(
            FoodRepository foods,
            FoodNutrientRepository foodNutrients,
            FoodServingRepository foodServings,
            IngredientRepository ingredients,
            IngredientFoodRepository ingredientFoods,
            IngredientUnitConversionRepository conversions) {
        this.foods = foods;
        this.foodNutrients = foodNutrients;
        this.foodServings = foodServings;
        this.ingredients = ingredients;
        this.ingredientFoods = ingredientFoods;
        this.conversions = conversions;
    }

    @Transactional(readOnly = true)
    public RecipeNutritionCatalogSnapshot resolve(
            Collection<Long> ingredientIds,
            Collection<Long> pinnedFoodIds) {

        Set<Long> requestedIngredientIds = requiredIds(ingredientIds);
        Set<Long> requestedPinnedFoodIds = optionalIds(pinnedFoodIds);

        List<Ingredient> ingredientRows = requestedIngredientIds.isEmpty()
                ? List.of()
                : ingredients.findAllWithDefaultFoodByIdIn(requestedIngredientIds);
        Map<Long, Ingredient> ingredientById = indexIngredients(
                ingredientRows,
                requestedIngredientIds);

        List<IngredientFood> mappingRows = requestedIngredientIds.isEmpty()
                ? List.of()
                : ingredientFoods.findByIngredientIdsWithFood(requestedIngredientIds);
        List<IngredientUnitConversion> conversionRows = requestedIngredientIds.isEmpty()
                ? List.of()
                : conversions.findByIngredientIdsWithUnits(requestedIngredientIds);

        Set<Long> foodIds = new LinkedHashSet<>(requestedPinnedFoodIds);
        for (Ingredient ingredient : ingredientRows) {
            if (ingredient.defaultFood() != null) {
                requireInternalId(ingredient.defaultFood().internalId(),
                        "ingredient.defaultFood");
                foodIds.add(ingredient.defaultFood().internalId());
            }
        }
        for (IngredientFood mapping : mappingRows) {
            foodIds.add(requireInternalId(mapping.foodId(), "ingredient_foods.food_id"));
        }

        List<Food> foodRows = foodIds.isEmpty()
                ? List.of()
                : foods.findAllById(foodIds);
        Map<Long, Food> foodById = indexFoods(foodRows, foodIds);

        List<FoodNutrient> nutrientRows = foodIds.isEmpty()
                ? List.of()
                : foodNutrients.findByFoodIdsWithNutrientAndUnit(foodIds);
        List<FoodServing> servingRows = foodIds.isEmpty()
                ? List.of()
                : foodServings.findByFoodIdsWithUnit(foodIds);

        Map<Long, List<FoodNutrient>> nutrientsByFood = groupNutrients(nutrientRows);
        Map<Long, List<FoodServing>> servingsByFood = groupServings(servingRows);
        Map<Long, List<IngredientFood>> mappingsByIngredient = groupMappings(mappingRows);
        Map<Long, List<IngredientUnitConversion>> conversionsByIngredient =
                groupConversions(conversionRows);

        Map<Long, IngredientNutrition> ingredientFacts = new LinkedHashMap<>();
        for (Long ingredientId : requestedIngredientIds) {
            Ingredient ingredient = ingredientById.get(ingredientId);
            Long defaultFoodId = ingredient.defaultFood() == null
                    ? null
                    : ingredient.defaultFood().internalId();
            List<FoodMapping> mappings = mappingsByIngredient
                    .getOrDefault(ingredientId, List.of())
                    .stream()
                    .map(mapping -> new FoodMapping(
                            requireInternalId(mapping.foodId(), "ingredient_foods.food_id"),
                            mapping.yieldFactor(),
                            mapping.isPrimary()))
                    .toList();
            List<IngredientConversion> ingredientConversions = conversionsByIngredient
                    .getOrDefault(ingredientId, List.of())
                    .stream()
                    .map(conversion -> new IngredientConversion(
                            unit(conversion.fromUnit()),
                            conversion.fromQuantity(),
                            unit(conversion.toUnit()),
                            conversion.toQuantity()))
                    .toList();
            ingredientFacts.put(
                    ingredientId,
                    new IngredientNutrition(
                            ingredientId,
                            defaultFoodId,
                            ingredient.pieceGramWeight(),
                            mappings,
                            ingredientConversions));
        }

        Map<Long, FoodNutrition> foodFacts = new LinkedHashMap<>();
        for (Long foodId : foodIds) {
            Food food = foodById.get(foodId);
            List<NutrientFact> nutrientsForFood = nutrientsByFood
                    .getOrDefault(foodId, List.of())
                    .stream()
                    .map(fact -> new NutrientFact(
                            requireInternalId(fact.nutrient().id(), "nutrient.id"),
                            requireText(fact.nutrient().code(), "nutrient.code"),
                            fact.amount()))
                    .toList();
            List<FoodServingFact> servingsForFood = servingsByFood
                    .getOrDefault(foodId, List.of())
                    .stream()
                    .map(serving -> new FoodServingFact(
                            serving.displayName(),
                            serving.quantity(),
                            unit(serving.unit()),
                            serving.gramWeight(),
                            serving.milliliters(),
                            serving.isDefaultServing()))
                    .toList();
            foodFacts.put(
                    foodId,
                    new FoodNutrition(
                            foodId,
                            food.nutritionBasis(),
                            food.densityGPerMl(),
                            food.revision(),
                            nutrientsForFood,
                            servingsForFood));
        }

        return new RecipeNutritionCatalogSnapshot(ingredientFacts, foodFacts);
    }

    private static Map<Long, Ingredient> indexIngredients(
            List<Ingredient> values,
            Set<Long> requestedIds) {
        Map<Long, Ingredient> indexed = new LinkedHashMap<>();
        for (Ingredient value : values) {
            if (value == null || value.internalId() == null) {
                throw inconsistentCatalog();
            }
            indexed.put(value.internalId(), value);
        }
        if (indexed.keySet().containsAll(requestedIds)
                && indexed.size() == requestedIds.size()) {
            return indexed;
        }
        throw inconsistentCatalog();
    }

    private static Map<Long, Food> indexFoods(
            List<Food> values,
            Set<Long> requestedIds) {
        Map<Long, Food> indexed = new LinkedHashMap<>();
        for (Food value : values) {
            if (value == null
                    || value.internalId() == null
                    || value.nutritionBasis() == null
                    || value.revision() == null
                    || value.revision() < 0
                    || (value.densityGPerMl() != null
                    && value.densityGPerMl().signum() <= 0)) {
                throw inconsistentCatalog();
            }
            indexed.put(value.internalId(), value);
        }
        if (indexed.keySet().containsAll(requestedIds)
                && indexed.size() == requestedIds.size()) {
            return indexed;
        }
        throw inconsistentCatalog();
    }

    private static Map<Long, List<FoodNutrient>> groupNutrients(
            List<FoodNutrient> values) {
        Map<Long, List<FoodNutrient>> grouped = new HashMap<>();
        for (FoodNutrient value : values) {
            if (value == null || value.food() == null || value.food().internalId() == null
                    || value.nutrient() == null || value.nutrient().id() == null
                    || value.amount() == null || value.amount().signum() < 0) {
                throw inconsistentCatalog();
            }
            grouped.computeIfAbsent(value.food().internalId(), key -> new java.util.ArrayList<>())
                    .add(value);
        }
        grouped.values().forEach(list -> list.sort(Comparator.comparing(value -> value.nutrient().id())));
        return grouped;
    }

    private static Map<Long, List<FoodServing>> groupServings(
            List<FoodServing> values) {
        Map<Long, List<FoodServing>> grouped = new HashMap<>();
        for (FoodServing value : values) {
            if (value == null || value.food() == null || value.food().internalId() == null
                    || value.unit() == null || value.quantity() == null
                    || value.quantity().signum() <= 0) {
                throw inconsistentCatalog();
            }
            grouped.computeIfAbsent(value.food().internalId(), key -> new java.util.ArrayList<>())
                    .add(value);
        }
        return grouped;
    }

    private static Map<Long, List<IngredientFood>> groupMappings(
            List<IngredientFood> values) {
        Map<Long, List<IngredientFood>> grouped = new HashMap<>();
        for (IngredientFood value : values) {
            if (value == null || value.ingredientId() == null || value.foodId() == null
                    || value.yieldFactor() == null || value.yieldFactor().signum() <= 0) {
                throw inconsistentCatalog();
            }
            grouped.computeIfAbsent(value.ingredientId(), key -> new java.util.ArrayList<>())
                    .add(value);
        }
        return grouped;
    }

    private static Map<Long, List<IngredientUnitConversion>> groupConversions(
            List<IngredientUnitConversion> values) {
        Map<Long, List<IngredientUnitConversion>> grouped = new HashMap<>();
        for (IngredientUnitConversion value : values) {
            if (value == null || value.ingredientId() == null
                    || value.fromUnit() == null || value.toUnit() == null
                    || value.fromQuantity() == null || value.toQuantity() == null) {
                throw inconsistentCatalog();
            }
            grouped.computeIfAbsent(value.ingredientId(), key -> new java.util.ArrayList<>())
                    .add(value);
        }
        return grouped;
    }

    private static Unit unit(com.smartmealplanner.nutrition.persistence.MeasurementUnit value) {
        if (value == null || value.id() == null || value.code() == null
                || value.unitType() == null) {
            throw inconsistentCatalog();
        }
        MeasurementUnit base = value.baseUnit();
        if (base != null && (base.id() == null || base.code() == null)) {
            throw inconsistentCatalog();
        }
        if ((base == null) != (value.factorToBaseUnit() == null)) {
            throw inconsistentCatalog();
        }
        return new Unit(
                value.id(),
                value.code(),
                value.unitType(),
                base == null ? null : base.id(),
                base == null ? null : base.code(),
                value.factorToBaseUnit());
    }

    private static Set<Long> requiredIds(Collection<Long> ids) {
        Set<Long> result = optionalIds(ids);
        if (result.stream().anyMatch(value -> value == null || value <= 0)) {
            throw inconsistentCatalog();
        }
        return result;
    }

    private static Set<Long> optionalIds(Collection<Long> ids) {
        if (ids == null) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw inconsistentCatalog();
            }
            result.add(id);
        }
        return result;
    }

    private static Long requireInternalId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(field + " is inconsistent");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " is inconsistent");
        }
        return value;
    }

    private static IllegalStateException inconsistentCatalog() {
        return new IllegalStateException("Nutrition catalog data is inconsistent");
    }
}
