package com.smartmealplanner.food;

import java.math.BigDecimal;

/** Explicit outcome: absent conversion facts never trigger a guessed conversion. */
public record IngredientConversionResult(
        boolean available,
        BigDecimal convertedQuantity) {

    static IngredientConversionResult unavailable() {
        return new IngredientConversionResult(false, null);
    }

    static IngredientConversionResult available(BigDecimal quantity) {
        return new IngredientConversionResult(true, quantity);
    }
}
