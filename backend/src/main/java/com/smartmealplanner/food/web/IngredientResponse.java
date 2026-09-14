package com.smartmealplanner.food.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.food.DislikedIngredientStrength;
import com.smartmealplanner.food.IngredientAllergenPresence;
import com.smartmealplanner.food.IngredientPreparationState;
import com.smartmealplanner.food.IngredientUnitConversionConfidence;

/** Public, surrogate-ID-free HTTP representations for Ingredient catalog reads. */
public final class IngredientResponse {
    private IngredientResponse() { }
    public record Item(UUID publicId, String code, String displayName, FoodResponse.Category category, boolean staple) { }
    public record FoodMapping(UUID foodPublicId, String foodCode, String foodDisplayName,
                              IngredientPreparationState preparationState, BigDecimal yieldFactor, boolean primary) { }
    public record Allergen(String allergenCode, String allergenDisplayName, IngredientAllergenPresence presence, String note) { }
    public record UnitConversion(String fromUnitCode, String fromUnitDisplayName, BigDecimal fromQuantity,
                                 String toUnitCode, String toUnitDisplayName, BigDecimal toQuantity,
                                 IngredientUnitConversionConfidence confidence, String sourceNote) { }
    public record Detail(UUID publicId, String code, String displayName, FoodResponse.Category category,
                         UUID defaultFoodPublicId, String defaultFoodCode, String defaultFoodDisplayName,
                         String defaultUnitCode, String defaultUnitDisplayName, BigDecimal pieceGramWeight,
                         Short typicalShelfLifeDays, boolean staple, List<String> aliases,
                         List<FoodMapping> foodMappings, List<Allergen> allergens,
                         List<UnitConversion> unitConversions) { }
    public record Page(int page, int size, long totalElements, int totalPages, List<Item> content) { }
    public record Disliked(UUID ingredientPublicId, String ingredientCode, String ingredientDisplayName,
                           FoodResponse.Category category, DislikedIngredientStrength strength, String note) { }
}
