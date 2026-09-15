package com.smartmealplanner.food;

import java.math.BigDecimal;

public record FoodServingView(
        String displayName,
        BigDecimal quantity,
        String unitCode,
        String unitDisplayName,
        BigDecimal gramWeight,
        BigDecimal milliliters,
        boolean defaultServing) {
}
