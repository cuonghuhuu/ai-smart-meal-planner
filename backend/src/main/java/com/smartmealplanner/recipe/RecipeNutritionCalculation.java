package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.Map;

record RecipeNutritionCalculation(
        Map<Long, BigDecimal> amountPerServingByNutrient,
        BigDecimal completenessRatio,
        int resolvedLineCount,
        int totalLineCount,
        int ingredientRevision,
        String computationNote) {

    RecipeNutritionCalculation {
        amountPerServingByNutrient = Map.copyOf(amountPerServingByNutrient);
    }
}
