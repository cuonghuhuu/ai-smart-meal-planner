package com.smartmealplanner.food;

import java.util.UUID;

public record IngredientSummaryView(
        UUID publicId,
        String code,
        String displayName,
        FoodCategoryView category,
        boolean staple) {
}
