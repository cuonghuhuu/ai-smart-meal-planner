package com.smartmealplanner.food;

import java.util.UUID;

public record DislikedIngredientView(
        UUID ingredientPublicId,
        String ingredientCode,
        String ingredientDisplayName,
        FoodCategoryView category,
        DislikedIngredientStrength strength,
        String note) {
}
