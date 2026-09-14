package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FoodDetailView(
        UUID publicId,
        String code,
        String displayName,
        String brand,
        FoodCategoryView category,
        String description,
        NutritionBasis nutritionBasis,
        BigDecimal densityGPerMl,
        FoodSource source,
        String sourceReference,
        Integer revision,
        List<FoodNutrientView> nutrients,
        List<FoodServingView> servings) {
}
