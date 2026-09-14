package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record IngredientDetailView(
        UUID publicId,
        String code,
        String displayName,
        FoodCategoryView category,
        UUID defaultFoodPublicId,
        String defaultFoodCode,
        String defaultFoodDisplayName,
        String defaultUnitCode,
        String defaultUnitDisplayName,
        BigDecimal pieceGramWeight,
        Short typicalShelfLifeDays,
        boolean staple,
        List<String> aliases,
        List<IngredientFoodMappingView> foodMappings,
        List<IngredientAllergenView> allergens,
        List<IngredientUnitConversionView> unitConversions) {
}
