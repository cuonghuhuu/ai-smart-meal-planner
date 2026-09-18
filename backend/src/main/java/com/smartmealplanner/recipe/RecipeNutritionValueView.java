package com.smartmealplanner.recipe;

import java.math.BigDecimal;

public record RecipeNutritionValueView(
        String nutrientCode,
        String nutrientDisplayName,
        BigDecimal amountPerServing,
        String unitCode,
        String unitDisplayName) {
}
