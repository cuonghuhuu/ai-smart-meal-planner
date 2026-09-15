package com.smartmealplanner.food;

import java.math.BigDecimal;

public record FoodNutrientView(
        String nutrientCode,
        String nutrientDisplayName,
        BigDecimal amount,
        String unitCode,
        String unitDisplayName,
        FoodNutrientDataQuality dataQuality) {
}
