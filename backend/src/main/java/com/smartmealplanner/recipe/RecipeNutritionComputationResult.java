package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Result of an explicit Recipe nutrition recomputation. */
public record RecipeNutritionComputationResult(
        UUID recipePublicId,
        LocalDateTime computedAt,
        Integer ingredientRevision,
        BigDecimal completenessRatio,
        int resolvedLineCount,
        int totalLineCount) {
}
