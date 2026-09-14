package com.smartmealplanner.food;

import java.math.BigDecimal;

public record IngredientUnitConversionView(
        String fromUnitCode,
        String fromUnitDisplayName,
        BigDecimal fromQuantity,
        String toUnitCode,
        String toUnitDisplayName,
        BigDecimal toQuantity,
        IngredientUnitConversionConfidence confidence,
        String sourceNote) {
}
